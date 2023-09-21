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
    notificationService: NotificationService,
    playApplicationContext: AppConfig
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger with ClockNow {

  implicit val useThisFormatter = TolerantJsonApiDefinition.tolerantFormatApiDefinition

  def createOrUpdate(apiDefinition: ApiDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {

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

  private def checkAPIDefinitionForStatusChanges(apiDefinition: ApiDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
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

  def fetchByServiceName(serviceName: String): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByServiceName(serviceName)
  }

  def fetchByName(name: String): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByName(name)
  }

  def fetchByContext(context: ApiContext): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByContext(context)
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[ApiDefinition]] = {
    apiDefinitionRepository.fetchByServiceBaseUrl(serviceBaseUrl)
  }

  def delete(serviceName: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchByServiceName(serviceName) flatMap {
      case None             => successful(())
      case Some(definition) =>
        for {
          _ <- awsApiPublisher.delete(definition)
          _ <- apiDefinitionRepository.delete(definition.serviceName)
        } yield ()
    }
  }

  def fetchAllPublicAPIs(alsoIncludePrivateTrials: Boolean): Future[Seq[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(alsoIncludePrivateTrials))
  }

  def fetchAll: Future[Seq[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll()
  }

  def fetchAllPrivateAPIs(): Future[Seq[ApiDefinition]] = {

    def hasPrivateAccess(apiVersion: ApiVersion) = apiVersion.access match {
      case ApiAccess.Private(_, _) => true
      case _                       => false
    }

    def removePublicVersions(api: ApiDefinition) =
      api.copy(versions = api.versions.filter(hasPrivateAccess))

    for {
      apiDefinitions     <- apiDefinitionRepository.fetchAll()
      includesPrivateApis = apiDefinitions.filter(d => d.versions.exists(hasPrivateAccess))
      onlyPrivateApis     = includesPrivateApis.map(removePublicVersions)
    } yield onlyPrivateApis
  }

  def fetchAllAPIsForApplication(applicationId: ApplicationId, alsoIncludePrivateTrials: Boolean): Future[Seq[ApiDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(alsoIncludePrivateTrials, applicationId))
  }

  private def filterAPIsForApplications(alsoIncludePrivateTrials: Boolean, applicationIds: ApplicationId*): Seq[ApiDefinition] => Seq[ApiDefinition] = {
    _ flatMap {
      filterAPIForApplications(alsoIncludePrivateTrials, applicationIds: _*)(_)
    }
  }

  private def filterAPIForApplications(alsoIncludePrivateTrials: Boolean, applicationIds: ApplicationId*): ApiDefinition => Option[ApiDefinition] = { api =>
    val filteredVersions = api.versions.filter(_.access match {
      case ApiAccess.Private(whitelistedApplicationIds, isTrial) =>
        whitelistedApplicationIds.exists(s => applicationIds.contains(s)) || (isTrial && alsoIncludePrivateTrials)
      case _                                                     => true
    })

    if (filteredVersions.isEmpty) None
    else Some(api.copy(versions = filteredVersions))
  }

  def publishAllToAws()(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchAll().map(awsApiPublisher.publishAll)
  }

}
