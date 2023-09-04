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

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

import org.joda.time.{DateTime, DateTimeZone}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiContext
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersionNbr

@Singleton
class APIDefinitionService @Inject() (
    awsApiPublisher: AwsApiPublisher,
    apiDefinitionRepository: APIDefinitionRepository,
    notificationService: NotificationService,
    playApplicationContext: AppConfig
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  def createOrUpdate(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {

    def publish(): Future[Unit] = {
      (for {
        _ <- awsApiPublisher.publish(apiDefinition)
      } yield ()) recoverWith {
        case e: PublishingException =>
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
      definitionWithPublishTime = apiDefinition.copy(lastPublishedAt = Some(DateTime.now(DateTimeZone.UTC)))
      _                        <- apiDefinitionRepository.save(definitionWithPublishTime) recoverWith recoverSave
    } yield ()
  }

  private def checkAPIDefinitionForStatusChanges(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    def findStatusDifferences(existingAPIVersions: Seq[APIVersion], newAPIVersions: Seq[APIVersion]): Seq[(ApiVersionNbr, APIStatus, APIStatus)] =
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
          .map(_.foreach(diff => notificationService.notifyOfStatusChange(apiDefinition.name, diff._1, diff._2, diff._3)))
      )
  }

  def fetchByServiceName(serviceName: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByServiceName(serviceName)
  }

  def fetchByName(name: String): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByName(name)
  }

  def fetchByContext(context: ApiContext): Future[Option[APIDefinition]] = {
    apiDefinitionRepository.fetchByContext(context)
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[APIDefinition]] = {
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

  def fetchAllPublicAPIs(alsoIncludePrivateTrials: Boolean): Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(alsoIncludePrivateTrials))
  }

  def fetchAll: Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll()
  }

  def fetchAllPrivateAPIs(): Future[Seq[APIDefinition]] = {

    def hasPrivateAccess(apiVersion: APIVersion) = apiVersion.access match {
      case Some(PrivateAPIAccess(_, _)) => true
      case _                            => false
    }

    def removePublicVersions(api: APIDefinition) =
      api.copy(versions = api.versions.filter(hasPrivateAccess))

    for {
      apiDefinitions     <- apiDefinitionRepository.fetchAll()
      includesPrivateApis = apiDefinitions.filter(d => d.versions.exists(hasPrivateAccess))
      onlyPrivateApis     = includesPrivateApis.map(removePublicVersions)
    } yield onlyPrivateApis
  }

  def fetchAllAPIsForApplication(applicationId: String, alsoIncludePrivateTrials: Boolean): Future[Seq[APIDefinition]] = {
    apiDefinitionRepository.fetchAll().map(filterAPIsForApplications(alsoIncludePrivateTrials, applicationId))
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
      case _                        => true
    })

    if (filteredVersions.isEmpty) None
    else Some(api.copy(versions = filteredVersions))
  }

  def publishAllToAws()(implicit hc: HeaderCarrier): Future[Unit] = {
    apiDefinitionRepository.fetchAll().map(awsApiPublisher.publishAll)
  }

}
