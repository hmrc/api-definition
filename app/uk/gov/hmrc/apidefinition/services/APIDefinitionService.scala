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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.TolerantJsonApiDefinition
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

object APIDefinitionService {

  case class PublishingException(message: String) extends Exception(message)
}

@Singleton
class APIDefinitionService @Inject() (
    val clock: Clock,
    awsApiPublisher: AwsApiPublisher,
    apiDefinitionRepository: APIDefinitionRepository,
    apiRemover: ApiRemover,
    notificationService: NotificationService,
    playApplicationContext: AppConfig
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger with ClockNow {

  implicit val useThisFormatter = TolerantJsonApiDefinition.tolerantFormatApiDefinition

  private def convertOne(stored: StoredApiDefinition): ApiDefinition = {
    ApiDefinition.fromStored(stored)
  }

  private def convertMany(storeds: Iterable[StoredApiDefinition]): List[ApiDefinition] = storeds.map(convertOne).toList

  def createOrUpdate(apiDefinition: StoredApiDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {

    def publish(): Future[Unit] = {
      (for {
        _ <- awsApiPublisher.publish(apiDefinition)
      } yield ()) recoverWith {
        case e: APIDefinitionService.PublishingException =>
          logger.error(s"Failed to create or update API [${apiDefinition.name}]", e)
          failed(new RuntimeException(s"Could not publish API: [${apiDefinition.name}]"))
      }
    }

    def recoverSave: PartialFunction[Throwable, Future[Nothing]] = {
      case e: Throwable =>
        logger.error(s"""API Definition for "${apiDefinition.name}" was published but not saved due to error: ${e.getMessage}""", e)
        failed(e)
    }

    for {
      _                        <- checkAPIDefinitionForStatusChanges(apiDefinition)
      _                        <- publish()
      definitionWithPublishTime = apiDefinition.copy(lastPublishedAt = Some(instant()))
      _                        <- apiDefinitionRepository.save(definitionWithPublishTime) recoverWith recoverSave
    } yield ()
  }

  private def checkAPIDefinitionForStatusChanges(apiDefinition: StoredApiDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    def findStatusDifferences(existingAPIVersions: Seq[ApiVersion], newAPIVersions: Seq[ApiVersion]): Seq[(ApiVersionNbr, ApiStatus, ApiStatus)] =
      (existingAPIVersions ++ newAPIVersions)
        .groupBy(_.versionNbr)
        .filter(v => v._2.size == 2)
        .filterNot(v => v._2.head.status == v._2.last.status)
        .map(v => (v._1, v._2.head.status, v._2.last.status))
        .toSeq

    apiDefinitionRepository.fetchByContext(apiDefinition.context)
      .map(existingAPIDefinitionOption =>
        existingAPIDefinitionOption
          .map(existingAPIDefinition => findStatusDifferences(existingAPIDefinition.versions, apiDefinition.versions))
          .map(_.foreach(diff => notificationService.notifyOfStatusChange(apiDefinition.name, diff._1, diff._2, diff._3)))
      )
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

  def fetchAllPublicAPIs(alsoIncludePrivateTrials: Boolean): Future[List[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll()
      .map(filterApisExcludingPrivate(alsoIncludePrivateTrials))
      .map(convertMany)
  }

  def fetchAll: Future[List[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll().map(convertMany)
  }

  def fetchAllPrivateAPIs(): Future[List[ApiDefinition]] = {

    def hasPrivateAccess(apiVersion: ApiVersion) = apiVersion.access match {
      case ApiAccess.Private(_) => true
      case _                    => false
    }

    def removePublicVersions(api: StoredApiDefinition) =
      api.copy(versions = api.versions.filter(hasPrivateAccess))

    for {
      apiDefinitions     <- apiDefinitionRepository.fetchAll()
      includesPrivateApis = apiDefinitions.filter(d => d.versions.exists(hasPrivateAccess))
      onlyPrivateApis     = includesPrivateApis.map(removePublicVersions)
    } yield onlyPrivateApis.toList.map(convertOne)
  }

  private def filterApisExcludingPrivate(alsoIncludePrivateTrials: Boolean): Seq[StoredApiDefinition] => Seq[StoredApiDefinition] = apis => {
    val innerFilter: StoredApiDefinition => Option[StoredApiDefinition] = api => {
      val filteredVersions = api.versions.filter(_.access match {
        case ApiAccess.Private(isTrial) => (isTrial && alsoIncludePrivateTrials)
        case _                          => true
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
      _                <- apiRemover.deleteUnusedApis()
      publishApiResult <- apiDefinitionRepository.fetchAll().flatMap(awsApiPublisher.publishAll)
    } yield publishApiResult

  }

}
