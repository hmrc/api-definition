/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.connector.ThirdPartyApplicationConnector
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.apidefinition.models._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class APIDefinitionService @Inject()(apiPublisher: WSO2APIPublisher,
                                     thirdPartyApplicationConnector: ThirdPartyApplicationConnector,
                                     apiDefinitionRepository: APIDefinitionRepository,
                                     appContext: AppContext) {

  def createOrUpdate(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[APIDefinition] = {

    def contextAlreadyDefinedForAnotherServiceName: Future[Boolean] = {
      apiDefinitionRepository.fetchByContext(apiDefinition.context).map {
        case Some(a: APIDefinition) => a.serviceName != apiDefinition.serviceName
        case _ => false
      }
    }

    def publish(): Future[Unit] = {
      apiPublisher.publish(apiDefinition)
    } recover {
      case e: PublishingException =>
        Logger.error(s"Failed to create or update API [${apiDefinition.name}]", e)
        Future.failed(new RuntimeException(s"Could not publish API: [${apiDefinition.name}]"))
    }

    contextAlreadyDefinedForAnotherServiceName.flatMap {
      case true => Future.failed(ContextAlreadyDefinedForAnotherService(apiDefinition.context, apiDefinition.serviceName))
      case false =>
        val definitionWithPublishTime = apiDefinition.copy(lastPublishedAt = Some(DateTime.now(DateTimeZone.UTC)))
        publish().flatMap {
          _ => apiDefinitionRepository.save(definitionWithPublishTime).map(_ => definitionWithPublishTime).recoverWith {
          case e: Throwable =>
            Logger.error(s"""API Definition for "${apiDefinition.name}" was published but not saved due to error: ${e.getMessage}""", e)
            Future.failed(e)
        }
      }
    }
  }

  def fetch(serviceName: String, email: Option[String])
           (implicit hc: HeaderCarrier): Future[Option[APIDefinition]] = {

    val maybeApiDefinitionF = apiDefinitionRepository.fetch(serviceName)
    val applicationIdsF = fetchApplicationIdsByEmail(email)

    for {
      api <- maybeApiDefinitionF
      userApplicationIds <- applicationIdsF
    } yield api.flatMap(filterAPIForApplications(userApplicationIds :_*))
  }

  def fetchExtended(serviceName: String, email: Option[String])
                   (implicit hc: HeaderCarrier): Future[Option[ExtendedAPIDefinition]] = {

    def appIsWhitelisted(userApplication: Seq[String], whitelist: Seq[String]) = {
      userApplication.intersect(whitelist).nonEmpty
    }

    def availability(version: APIVersion, userApplicationIds: Seq[String], forSandbox: Boolean = false): Option[APIAvailability] = {
      if (forSandbox == appContext.isSandbox) {
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

    val maybeApiDefinitionF = apiDefinitionRepository.fetch(serviceName)
    val applicationIdsF = fetchApplicationIdsByEmail(email)

    for {
      api <- maybeApiDefinitionF
      userApplicationIds <- applicationIdsF
    } yield api.map { a =>
        val versions = a.versions.foldLeft(Seq[ExtendedAPIVersion]()){
          case (acc, version) => acc :+
            ExtendedAPIVersion(version.version, version.status, version.endpoints,
              availability(version, userApplicationIds),
              availability(version, userApplicationIds, forSandbox = true))
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

  def fetchByContext(context: String)(implicit hc: HeaderCarrier): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByContext(context)
  }

  def delete(serviceName: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetch(serviceName) flatMap {
      case None => Future.successful(())
      case Some(definition) => apiPublisher.hasSubscribers(definition) flatMap {
        case true => Future.failed(new UnauthorizedException("API has subscribers"))
        case false =>
          apiPublisher.delete(definition) flatMap { _ =>
            apiDefinitionRepository.delete(definition.serviceName)
          }
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
    _ flatMap { api => filterAPIForApplications(applicationIds:_*)(api) }
  }

  private def filterAPIForApplications(applicationIds: String*) : APIDefinition => Option[APIDefinition] = { api =>

    val filteredVersions = api.versions.filter(_.access.getOrElse(PublicAPIAccess) match {
      case access: PrivateAPIAccess => access.whitelistedApplicationIds.exists(s => applicationIds.contains(s)) || access.isTrial.getOrElse(false)
      case _ => true
    })

    if (filteredVersions.isEmpty) {
      None
    } else {
      Some(api.copy(versions = filteredVersions))
    }
  }

  def publishAll()(implicit hc: HeaderCarrier): Future[Unit] = {

    def allFailures() = {
      for {
        definitions <- apiDefinitionRepository.fetchAll()
        fs <- apiPublisher.publish(definitions)
      } yield fs
    }

    allFailures().flatMap {
      case f if f.nonEmpty =>
        Future.failed(new RuntimeException(s"Could not republish the following APIs to WSO2: [${f.mkString(", ")}]"))
      case _ => Future.successful(())
    }
  }

}
