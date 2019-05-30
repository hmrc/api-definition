/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.connector.ThirdPartyApplicationConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import scala.collection.Seq
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class APIDefinitionService @Inject()(wso2Publisher: WSO2APIPublisher,
                                     awsApiPublisher: AwsApiPublisher,
                                     thirdPartyApplicationConnector: ThirdPartyApplicationConnector,
                                     apiDefinitionRepository: APIDefinitionRepository,
                                     playApplicationContext: AppContext)
                                    (implicit val ec: ExecutionContext) {

  def createOrUpdate(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {

    def publish(): Future[Unit] = {
      (for {
        _ <- wso2Publisher.publish(apiDefinition)
        _ <- awsApiPublisher.publish(apiDefinition)
      } yield ()) recoverWith {
      case e: PublishingException =>
        Logger.error(s"Failed to create or update API [${apiDefinition.name}]", e)
        failed(new RuntimeException(s"Could not publish API: [${apiDefinition.name}]"))
      }
    }

    publish().flatMap { _ =>
      val definitionWithPublishTime = apiDefinition.copy(lastPublishedAt = Some(DateTime.now(DateTimeZone.UTC)))

      apiDefinitionRepository.save(definitionWithPublishTime)
        .map(_ => ())
        .recoverWith {
          case e: Throwable =>
            Logger.error(s"""API Definition for "${apiDefinition.name}" was published but not saved due to error: ${e.getMessage}""", e)
            failed(e)
        }
    }
  }

  def fetchByServiceName(serviceName: String, email: Option[String])
                        (implicit hc: HeaderCarrier): Future[Option[APIDefinition]] = {

    val maybeApiDefinitionF = apiDefinitionRepository.fetchByServiceName(serviceName)
    val applicationIdsF = fetchApplicationIdsByEmail(email)

    for {
      api <- maybeApiDefinitionF
      userApplicationIds <- applicationIdsF
    } yield api.flatMap(filterAPIForApplications(userApplicationIds :_*))
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
        val versions = a.versions.foldLeft(Seq[ExtendedAPIVersion]()){
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

  def fetchEndpointsByContextVersionAndScopes(context: String, version: String, scopes: Seq[String]): Future[Seq[Endpoint]] = {
    apiDefinitionRepository.fetchEndpointsByContextVersionAndScopes(context, version, scopes)
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByServiceBaseUrl(serviceBaseUrl)
  }

  def delete(serviceName: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchByServiceName(serviceName) flatMap {
      case None => successful(())
      case Some(definition) => wso2Publisher.hasSubscribers(definition) flatMap {
        case true => failed(new UnauthorizedException("API has subscribers"))
        case false =>
          for {
            _ <- wso2Publisher.delete(definition)
            _ <- awsApiPublisher.delete(definition)
            _ <- apiDefinitionRepository.delete(definition.serviceName)
          } yield ()
      }
    }
  }

  def fetchAllPublicAPIs(): Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications())
  }

  def fetchAllPrivateAPIs(): Future[Seq[APIDefinition]] = {

    def hasPrivateAccess(apiVersion: APIVersion) = apiVersion.access match {
      case Some(PrivateAPIAccess(_, _)) => true
      case _ => false
    }

    def removePublicVersions(api: APIDefinition) =
      api.copy(versions= api.versions.filter(hasPrivateAccess))

    for {
      apiDefinitions <- apiDefinitionRepository.fetchAll()
      includesPrivateApis = apiDefinitions.filter(d => d.versions.exists(hasPrivateAccess))
      onlyPrivateApis = includesPrivateApis.map(removePublicVersions)
    } yield onlyPrivateApis
  }

  def fetchAllAPIsForApplication(applicationId: String): Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(applicationId))
  }

  def fetchAllAPIsForCollaborator(email: String)(implicit hc: HeaderCarrier): Future[Seq[APIDefinition]] = {

    val applicationIdsF = fetchApplicationIdsByEmail(Some(email))
    val apiDefinitionsF = apiDefinitionRepository.fetchAll()

    for {
      userApplicationIds <- applicationIdsF
      allApis <- apiDefinitionsF
    } yield filterAPIsForApplications(userApplicationIds :_*)(allApis)
  }

  private def filterAPIsForApplications(applicationIds: String*) : Seq[APIDefinition] => Seq[APIDefinition] = {
    _ flatMap { filterAPIForApplications(applicationIds:_*)(_) }
  }

  private def filterAPIForApplications(applicationIds: String*) : APIDefinition => Option[APIDefinition] = { api =>

    val filteredVersions = api.versions.filter(_.access.getOrElse(PublicAPIAccess) match {
      case access: PrivateAPIAccess => access.whitelistedApplicationIds.exists(s => applicationIds.contains(s)) || access.isTrial.getOrElse(false)
      case _ => true
    })

    if (filteredVersions.isEmpty) None
    else Some(api.copy(versions = filteredVersions))
  }

  def publishAll()(implicit hc: HeaderCarrier): Future[Unit] = {

    def allFailures() = {
      for {
        definitions <- apiDefinitionRepository.fetchAll()
        fs <- wso2Publisher.publish(definitions)
      } yield fs
    }

    allFailures().flatMap {
      case f if f.nonEmpty => failed(new RuntimeException(s"Could not republish the following APIs to WSO2: [${f.mkString(", ")}]"))
      case _ => successful(())
    }
  }

  def publishAllToAws()(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchAll().map(awsApiPublisher.publishAll)
  }

}
