/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apidefinition.services

import javax.inject.{Inject, Singleton}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ThirdPartyApplicationConnector
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import scala.concurrent.Future.{failed, sequence, successful}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class APIDefinitionService @Inject()(awsApiPublisher: AwsApiPublisher,
                                     thirdPartyApplicationConnector: ThirdPartyApplicationConnector,
                                     apiDefinitionRepository: APIDefinitionRepository,
                                     notificationService: NotificationService,
                                     playApplicationContext: AppConfig)
                                    (implicit val ec: ExecutionContext) {

  def createOrUpdate(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {

    def publish(): Future[Unit] = {
      (for {
        _ <- awsApiPublisher.publish(apiDefinition)
      } yield ()) recoverWith {
        case e: PublishingException =>
          Logger.error(s"Failed to create or update API [${apiDefinition.name}]", e)
          failed(new RuntimeException(s"Could not publish API: [${apiDefinition.name}]"))
      }
    }

    def recoverSave: PartialFunction[Throwable, Future[Nothing]] = {
      case e: Throwable =>
        Logger.error(s"""API Definition for "${apiDefinition.name}" was published but not saved due to error: ${e.getMessage}""", e)
        failed(e)
    }

    for {
      _ <- checkAPIDefinitionForStatusChanges(apiDefinition)
      _ <- publish()
      definitionWithPublishTime = apiDefinition.copy(lastPublishedAt = Some(DateTime.now(DateTimeZone.UTC)))
      _ <- apiDefinitionRepository.save(definitionWithPublishTime) recoverWith recoverSave
    } yield ()
  }

  private def checkAPIDefinitionForStatusChanges(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    def findStatusDifferences(existingAPIVersions: Seq[APIVersion], newAPIVersions: Seq[APIVersion]): Seq[(String, APIStatus, APIStatus)] =
        (existingAPIVersions ++ newAPIVersions)
          .groupBy(_.version)
          .filter(v => v._2.size == 2)
          .filterNot(v => v._2.head.status == v._2.last.status)
          .map(v => (v._1, v._2.head.status, v._2.last.status))
          .toSeq

    apiDefinitionRepository.fetchByContext(apiDefinition.context)
      .map(existingAPIDefinitionOption =>
        existingAPIDefinitionOption
          .map(existingAPIDefinition => findStatusDifferences(existingAPIDefinition.versions, apiDefinition.versions))
          .map(_.foreach(diff => notificationService.notifyOfStatusChange(apiDefinition.name, diff._1, diff._2, diff._3))))
  }

  def fetchByServiceName(serviceName: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByServiceName(serviceName)
  }

  def fetchExtendedByServiceName(serviceName: String, email: Option[String])
                                (implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] = {

    def appIsWhitelisted(userApplication: Seq[String], whitelist: Seq[String]) = {
      userApplication.intersect(whitelist).nonEmpty
    }

    def availability(version: APIVersion, userApplicationIds: Seq[String], forSandbox: Boolean = false): Option[APIAvailability] = {
      if (forSandbox == playApplicationContext.isSandbox) {
        version.access match {
          case Some(PrivateAPIAccess(whitelist, isTrial)) => Some(APIAvailability(version.endpointsEnabled.getOrElse(false),
            PrivateAPIAccess(whitelist, isTrial),
            email.isDefined,
            authorised = appIsWhitelisted(userApplicationIds, whitelist)))

          case _ => Some(APIAvailability(version.endpointsEnabled.getOrElse(false),
            PublicAPIAccess(),
            email.isDefined,
            authorised = true))
        }
      } else None
    }

    val maybeApiDefinitionF = apiDefinitionRepository.fetchByServiceName(serviceName)
    val applicationIdsF = fetchApplicationIdsByEmail(email)

    for {
      api <- maybeApiDefinitionF
      userApplicationIds <- applicationIdsF
    } yield api.map { a =>
      val versions = a.versions.foldLeft(Seq[ExtendedAPIVersion]()) {
        case (acc, version) => acc :+
          ExtendedAPIVersion(
            version = version.version,
            status = version.status,
            endpoints = version.endpoints,
            productionAvailability = availability(version, userApplicationIds),
            sandboxAvailability = availability(version, userApplicationIds, forSandbox = true))
      }

      ExtendedAPIDefinition(a.serviceName,
        a.serviceBaseUrl,
        a.name,
        a.description,
        a.context,
        a.requiresTrust.getOrElse(false),
        a.isTestSupport.getOrElse(false),
        versions,
        a.lastPublishedAt)
    }
  }

  private def fetchApplicationIdsByEmail(email: Option[String])(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    email match {
      case None => Future(Seq.empty)
      case Some(e) =>
        for {
          application <- thirdPartyApplicationConnector.fetchApplicationsByEmail(e)
        } yield application.map(_.id.toString)
    }
  }

  def fetchByName(name: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByName(name)
  }

  def fetchByContext(context: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByContext(context)
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByServiceBaseUrl(serviceBaseUrl)
  }

  def delete(serviceName: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchByServiceName(serviceName) flatMap {
      case None => successful(())
      case Some(definition) => hasSubscribers(definition) flatMap {
        case true => failed(new UnauthorizedException("API has subscribers"))
        case false =>
          for {
            _ <- awsApiPublisher.delete(definition)
            _ <- apiDefinitionRepository.delete(definition.serviceName)
          } yield ()
      }
    }
  }

  private def hasSubscribers(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Boolean] = {
    sequence {
      apiDefinition.versions.map(v => thirdPartyApplicationConnector.fetchSubscribers(apiDefinition.context, v.version))
    } map {
      _.exists(subscribers => subscribers.nonEmpty)
    }
  }

  def fetchAllPublicAPIs(alsoIncludePrivateTrials: Boolean): Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(alsoIncludePrivateTrials))
  }

  def fetchAll: Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll()
  }

  def fetchAllPrivateAPIs(): Future[Seq[APIDefinition]] = {

    def hasPrivateAccess(apiVersion: APIVersion) = apiVersion.access match {
      case Some(PrivateAPIAccess(_, _)) => true
      case _ => false
    }

    def removePublicVersions(api: APIDefinition) =
      api.copy(versions = api.versions.filter(hasPrivateAccess))

    for {
      apiDefinitions <- apiDefinitionRepository.fetchAll()
      includesPrivateApis = apiDefinitions.filter(d => d.versions.exists(hasPrivateAccess))
      onlyPrivateApis = includesPrivateApis.map(removePublicVersions)
    } yield onlyPrivateApis
  }

  def fetchAllAPIsForApplication(applicationId: String, alsoIncludePrivateTrials: Boolean): Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(alsoIncludePrivateTrials, applicationId))
  }

  def fetchAllAPIsForCollaborator(email: String, alsoIncludePrivateTrials: Boolean)(implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] = {

    val applicationIdsF = fetchApplicationIdsByEmail(Some(email))
    val apiDefinitionsF = apiDefinitionRepository.fetchAll()

    for {
      userApplicationIds <- applicationIdsF
      allApis <- apiDefinitionsF
    } yield filterAPIsForApplications(alsoIncludePrivateTrials, userApplicationIds: _*)(allApis)
  }

  private def filterAPIsForApplications(alsoIncludePrivateTrials: Boolean, applicationIds: String*): Seq[APIDefinition] => Seq[APIDefinition] = {
    _ flatMap {
      filterAPIForApplications(alsoIncludePrivateTrials, applicationIds: _*)(_)
    }
  }

  private def filterAPIForApplications(alsoIncludePrivateTrials: Boolean, applicationIds: String*): APIDefinition => Option[APIDefinition] = { api =>

    val filteredVersions = api.versions.filter(_.access.getOrElse(PublicAPIAccess) match {
      case access: PrivateAPIAccess =>
        access.whitelistedApplicationIds.exists(s => applicationIds.contains(s)) || (access.isTrial.contains(true) && alsoIncludePrivateTrials)
      case _ => true
    })

    if (filteredVersions.isEmpty) None
    else Some(api.copy(versions = filteredVersions))
  }

  def publishAllToAws()(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchAll().map(awsApiPublisher.publishAll)
  }

}
