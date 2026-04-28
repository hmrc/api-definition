/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.OFormat
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.ApiEvents._
import uk.gov.hmrc.apidefinition.models.{ApiEvent, ApiEventId, TolerantJsonApiDefinition}
import uk.gov.hmrc.apidefinition.repository.{APIDefinitionRepository, APIEventRepository}
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import uk.gov.hmrc.apidefinition.validators.{ApiDefinitionValidator, Validator}

object APIDefinitionService {

  case class PublishingException(message: String) extends Exception(message)
}

@Singleton
class APIDefinitionService @Inject() (
    val clock: Clock,
    awsApiPublisher: AwsApiPublisher,
    apiDefinitionRepository: APIDefinitionRepository,
    apiEventRepository: APIEventRepository,
    apiRemover: ApiRemover,
    apiRetirer: ApiRetirer,
    notificationService: NotificationService,
    config: AppConfig
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger with ClockNow with Validator[StoredApiDefinition] {

  implicit val useThisFormatter: OFormat[StoredApiDefinition] = TolerantJsonApiDefinition.tolerantFormatApiDefinition

  private def convertOne(stored: StoredApiDefinition): ApiDefinition = {
    ApiDefinition.fromStored(stored)
  }

  private def convertMany(storeds: Iterable[StoredApiDefinition]): List[ApiDefinition] = storeds.map(convertOne).toList

  def validate(requestedDefn: StoredApiDefinition): Future[HMRCValidatedNel[StoredApiDefinition]] = {
    val validated: HMRCValidatedNel[StoredApiDefinition] = ApiDefinitionValidator.validateKeysArePresent(requestedDefn)

    (if (validated.isValid) {
       for {
         existingApiDefn      <- apiDefinitionRepository.fetchByServiceName(requestedDefn.serviceName)
         skipContextValidation = config.skipContextValidationAllowlist.contains(requestedDefn.serviceName)

         byContext        <- apiDefinitionRepository.fetchByContext(requestedDefn.context)
         byServiceBaseUrl <- apiDefinitionRepository.fetchByServiceBaseUrl(requestedDefn.serviceBaseUrl)
         byName           <- apiDefinitionRepository.fetchByName(requestedDefn.name)

         otherContextsWithSameTopLevel <- apiDefinitionRepository.fetchAllByTopLevelContext(requestedDefn.context.topLevelContext())
                                            .map(_.filterNot(_.context == requestedDefn.context)) // Remove the requested api definition
                                            .map(_.map(_.context).toList)
         result                         = ApiDefinitionValidator.validate(requestedDefn, existingApiDefn, byContext, byServiceBaseUrl, byName, otherContextsWithSameTopLevel, skipContextValidation)
       } yield result
     } else {
       successful(validated)
     })
  }

  def createOrUpdate(newApiDefn: StoredApiDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {

    def publish(): Future[Unit] = {
      (for {
        _ <- awsApiPublisher.publish(newApiDefn)
      } yield ()) recoverWith {
        case e: APIDefinitionService.PublishingException =>
          logger.error(s"Failed to create or update API [${newApiDefn.name}]", e)
          failed(new RuntimeException(s"Could not publish API: [${newApiDefn.name}]"))
      }
    }

    def recoverSave: PartialFunction[Throwable, Future[Nothing]] = {
      case e: Throwable =>
        logger.error(s"""API Definition for "${newApiDefn.name}" was published but not saved due to error: ${e.getMessage}""", e)
        failed(e)
    }

    for {
      existingApiDefn <- apiDefinitionRepository.fetchByContext(newApiDefn.context)

      events = checkAPIDefinitionForChanges(newApiDefn, existingApiDefn)
      _     <- apiEventRepository.createAll(events)
      _     <- notificationService.process(events)

      _                        <- publish()
      definitionWithPublishTime = newApiDefn.copy(lastPublishedAt = Some(instant))
      _                        <- apiDefinitionRepository.save(definitionWithPublishTime) recoverWith recoverSave
    } yield ()
  }

  def findApiEvents(apiName: String, serviceName: ServiceName, existingAPIVersions: Seq[ApiVersion], newAPIVersions: Seq[ApiVersion]): List[ApiEvent] = {
    val versionPairs = (existingAPIVersions ++ newAPIVersions)
      .groupBy(_.versionNbr)
      .filter(v => v._2.size == 2)

    val findStatusDifferences = versionPairs
      .filterNot(v => v._2.head.status == v._2.last.status)
      .map(v => ApiVersionStatusChange(ApiEventId.random, apiName, serviceName, instant, v._2.head.status, v._2.last.status, v._1))
      .toList

    val findAccessDifferences = versionPairs
      .filterNot(v => v._2.head.access == v._2.last.access)
      .map(v => ApiVersionAccessChange(ApiEventId.random, apiName, serviceName, instant, v._2.head.access, v._2.last.access, v._1))
      .toList

    val findEndpointsAdded = versionPairs
      .filter(v => checkEndpointsAdded(v._2.head.endpoints, v._2.last.endpoints))
      .map(v => ApiVersionEndpointsAdded(ApiEventId.random, apiName, serviceName, instant, listEndpointsAdded(v._2.head.endpoints, v._2.last.endpoints), v._1))
      .toList

    val findEndpointsRemoved = versionPairs
      .filter(v => checkEndpointsRemoved(v._2.head.endpoints, v._2.last.endpoints))
      .map(v => ApiVersionEndpointsRemoved(ApiEventId.random, apiName, serviceName, instant, listEndpointsRemoved(v._2.head.endpoints, v._2.last.endpoints), v._1))
      .toList

    val findNewVersion = newAPIVersions
      .filterNot(newVersion => existingAPIVersions.map(_.versionNbr).contains(newVersion.versionNbr))
      .map(newVersion => NewApiVersion(ApiEventId.random, apiName, serviceName, instant, newVersion.status, newVersion.versionNbr))

    findStatusDifferences ++ findAccessDifferences ++ findEndpointsAdded ++ findEndpointsRemoved ++ findNewVersion
  }

  private def checkEndpointsAdded(oldEndpoints: List[Endpoint], newEndpoints: List[Endpoint]): Boolean = {
    !listEndpointsAdded(oldEndpoints, newEndpoints).isEmpty
  }

  private def listEndpointsAdded(oldEndpoints: List[Endpoint], newEndpoints: List[Endpoint]): List[Endpoint] = {
    // Go through new list, checking for the existance of an old one
    newEndpoints.filterNot(ne => isEndpointPresentInList(ne, oldEndpoints))
  }

  private def checkEndpointsRemoved(oldEndpoints: List[Endpoint], newEndpoints: List[Endpoint]): Boolean = {
    !listEndpointsRemoved(oldEndpoints, newEndpoints).isEmpty
  }

  private def listEndpointsRemoved(oldEndpoints: List[Endpoint], newEndpoints: List[Endpoint]): List[Endpoint] = {
    // Go through old list, checking for the existance of a new one
    oldEndpoints.filterNot(oe => isEndpointPresentInList(oe, newEndpoints))
  }

  private def isEndpointPresentInList(endpoint: Endpoint, listOfEndpoints: List[Endpoint]): Boolean = {
    // Note that we are assuming that an endpoint is equal if the method and URL are the same
    listOfEndpoints.exists(e => (e.method == endpoint.method && e.uriPattern == endpoint.uriPattern))
  }

  private def checkAPIDefinitionForChanges(apiDefinition: StoredApiDefinition, existingApiDefn: Option[StoredApiDefinition]): List[ApiEvent] = {
    existingApiDefn
      .fold(List[ApiEvent](ApiCreated(ApiEventId.random, apiDefinition.name, apiDefinition.serviceName, instant))) { defn =>
        val events = findApiEvents(apiDefinition.name, apiDefinition.serviceName, defn.versions, apiDefinition.versions)
        if (events.isEmpty) List(ApiPublishedNoChange(ApiEventId.random, apiDefinition.name, apiDefinition.serviceName, instant)) else events
      }
  }

  def fetchByServiceName(serviceName: ServiceName): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByServiceName(serviceName).map(_.map(convertOne))
  }

  def fetchByName(name: String): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByName(name).map(_.map(convertOne))
  }

  def fetchByContext(context: ApiContext): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByContext(context).map(_.map(convertOne))
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByServiceBaseUrl(serviceBaseUrl).map(_.map(convertOne))
  }

  def delete(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchByServiceName(serviceName) flatMap {
      case None             => successful(())
      case Some(definition) =>
        for {
          _ <- awsApiPublisher.delete(definition)
          _ <- apiDefinitionRepository.delete(definition.serviceName)
        } yield ()
    }
  }

  def fetchAllPublicAPIs(alsoIncludeControlledApis: Boolean): Future[List[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll()
      .map(filterApisExcludingNonPublic(alsoIncludeControlledApis))
      .map(convertMany)
  }

  def fetchAll: Future[List[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll().map(convertMany)
  }

  def fetchAllNonPublicAPIs(): Future[List[ApiDefinition]] = {

    def hasNonPublicAccess(apiVersion: ApiVersion) = !apiVersion.access.isPublic

    def removePublicVersions(api: StoredApiDefinition) =
      api.copy(versions = api.versions.filter(hasNonPublicAccess))

    for {
      apiDefinitions       <- apiDefinitionRepository.fetchAll()
      includesNonPublicApis = apiDefinitions.filter(d => d.versions.exists(hasNonPublicAccess))
      onlyNonPublicApis     = includesNonPublicApis.map(removePublicVersions)
    } yield onlyNonPublicApis.toList.map(convertOne)
  }

  private def filterApisExcludingNonPublic(alsoIncludeControlledApis: Boolean): Seq[StoredApiDefinition] => Seq[StoredApiDefinition] = apis => {
    val innerFilter: StoredApiDefinition => Option[StoredApiDefinition] = api => {
      val filteredVersions = api.versions.filter(_.access match {
        case ApiAccessType.PUBLIC     => true
        case ApiAccessType.INTERNAL   => false
        case ApiAccessType.CONTROLLED => alsoIncludeControlledApis
      })

      if (filteredVersions.isEmpty) None
      else Some(api.copy(versions = filteredVersions))
    }

    apis flatMap {
      innerFilter(_)
    }
  }

  def publishAllToAws()(implicit hc: HeaderCarrier): Future[Unit] = {
    for {
      _                <- if (config.apisToRetire.nonEmpty) apiRetirer.retireApis(config.apisToRetire) else Future.successful(())
      _                <- apiRemover.deleteUnusedApis()
      publishApiResult <- apiDefinitionRepository.fetchAll().flatMap(awsApiPublisher.publishAll)
    } yield publishApiResult

  }

  def fetchEventsByServiceName(serviceName: ServiceName, includeNoChange: Boolean = true): Future[List[ApiEvent]] =
    apiEventRepository.fetchEvents(serviceName, includeNoChange)

  def deleteEventsByServiceName(serviceName: ServiceName): Future[Unit] =
    apiEventRepository.deleteEvents(serviceName)
}
