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

package uk.gov.hmrc.apidefinition.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiStatus, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames._

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{APIDefinitionService, ApiRemover, AwsApiPublisher, NotificationService}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class APIDefinitionServiceSpec extends AsyncHmrcSpec with FixedClock {

  private val context     = ApiContext("calendar")
  private val serviceName = ServiceName("calendar-service")

  def unitSuccess: Future[Unit] = successful { () }

  private def aStoredApiDefinition(context: ApiContext, versions: ApiVersion*) =
    StoredApiDefinition(ServiceName("service"), "http://service", "name", "description", context, versions.toList, false, false, None, List(ApiCategory.OTHER))

  private def anApiDefinition(context: ApiContext, versions: ApiVersion*) =
    ApiDefinition(ServiceName("service"), "http://service", "name", "description", context, versions.map(v => v.versionNbr -> v).toMap, false, false, None, List(ApiCategory.OTHER))

  trait Setup {

    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val mockAwsApiPublisher: AwsApiPublisher                 = mock[AwsApiPublisher]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockNotificationService: NotificationService         = mock[NotificationService]
    val mockAppContext: AppConfig                            = mock[AppConfig]
    val mockApiRemover: ApiRemover                           = mock[ApiRemover]

    val underTest = new APIDefinitionService(
      FixedClock.clock,
      mockAwsApiPublisher,
      mockAPIDefinitionRepository,
      mockApiRemover,
      mockNotificationService,
      mockAppContext
    )

    val applicationId = ApplicationId.random
    val otherAppId    = ApplicationId.random

    val publicVersion1: ApiVersion = aVersion(version = ApiVersionNbr("1.0"), access = ApiAccess.PUBLIC)
    val publicVersion2             = aVersion(version = ApiVersionNbr("2.0"), access = ApiAccess.PUBLIC)
    val privateVersion: ApiVersion = aVersion(version = ApiVersionNbr("3.1"), access = ApiAccess.Private())
    val privateTrialVersion        = aVersion(version = ApiVersionNbr("4.0"), access = ApiAccess.Private(isTrial = true))

    val allVersions = List(
      publicVersion1,
      publicVersion2,
      privateVersion,
      privateTrialVersion
    )

    val publicVersion1Availability =
      ApiAvailability(publicVersion1.endpointsEnabled, ApiAccess.PUBLIC, loggedIn = false, authorised = true)

    val publicVersionAvailability = ApiAvailability(
      publicVersion2.endpointsEnabled,
      publicVersion2.access,
      loggedIn = false,
      authorised = true
    )

    val privateVersionAvailability = ApiAvailability(
      privateVersion.endpointsEnabled,
      privateVersion.access,
      loggedIn = false,
      authorised = false
    )

    val apiDefinitionWithAllVersions = aStoredApiDefinition(context, allVersions: _*)
    val apiDefinition                = someAPIDefinition
    val apiDefinitionWithSavingTime  = apiDefinition.copy(lastPublishedAt = Some(instant()))

    when(mockAPIDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)).thenReturn(successful(Some(apiDefinition)))
    when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(successful(()))
  }

  trait FetchSetup extends Setup {

    val versions         = Seq(
      publicVersion1,
      publicVersion2,
      privateVersion,
      privateTrialVersion
    )
    val storedDefinition = aStoredApiDefinition(context, versions: _*)
    val definition       = anApiDefinition(context, versions: _*)

    when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(storedDefinition)))
  }

  "createOrUpdate" should {

    "create or update the API Definition in all AWS and the repository" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(successful(apiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(apiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to publish an API to AWS" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to save the definition" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      verifyZeroInteractions(mockNotificationService)
    }

    "send notifications when version of API has changed status" in new Setup {
      val apiVersion                                              = ApiVersionNbr("1.0")
      val apiContext                                              = ApiContext("foo")
      val existingStatus: ApiStatus                               = ApiStatus.ALPHA
      val updatedStatus: ApiStatus                                = ApiStatus.BETA
      val existingAPIDefinition: StoredApiDefinition              = aStoredApiDefinition(apiContext, aVersion(apiVersion, existingStatus, ApiAccess.PUBLIC))
      val updatedAPIDefinition: StoredApiDefinition               = aStoredApiDefinition(apiContext, aVersion(apiVersion, updatedStatus, ApiAccess.PUBLIC))
      val updatedAPIDefinitionWithSavingTime: StoredApiDefinition = updatedAPIDefinition.copy(lastPublishedAt = Some(instant()))

      when(mockAPIDefinitionRepository.fetchByContext(apiContext)).thenReturn(successful(Some(existingAPIDefinition)))
      when(mockNotificationService.notifyOfStatusChange(existingAPIDefinition.name, apiVersion, existingStatus, updatedStatus)).thenReturn(unitSuccess)
      when(mockAwsApiPublisher.publish(updatedAPIDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(updatedAPIDefinitionWithSavingTime)).thenReturn(successful(updatedAPIDefinitionWithSavingTime))

      await(underTest.createOrUpdate(updatedAPIDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(updatedAPIDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(updatedAPIDefinitionWithSavingTime)
      verify(mockNotificationService).notifyOfStatusChange(existingAPIDefinition.name, apiVersion, existingStatus, updatedStatus)
    }
  }

  "fetch" should {
    "return API definition from the repository" in new FetchSetup {
      val response = await(underTest.fetchByServiceName(serviceName))

      response shouldBe Some(definition)
    }

    "return None when there is no matching API definition" in new FetchSetup {
      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(None))

      val response = await(underTest.fetchByServiceName(serviceName))

      response shouldBe None
    }
  }

  "fetchAllPublicAPIs" when {

    "the alsoIncludePrivateTrials option is false" should {

      val alsoIncludePrivateTrials = false

      "return all Public API Definitions and filter out private versions" in new Setup {

        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinitionWithAllVersions)))

        val result = await(underTest.fetchAllPublicAPIs(alsoIncludePrivateTrials))

        result shouldBe List(anApiDefinition(context, publicVersion1, publicVersion2))
      }
    }

    "the alsoIncludePrivateTrials option is true" should {

      val alsoIncludePrivateTrials = true

      "return all Public and Private Trial API Definitions" in new Setup {

        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinitionWithAllVersions)))

        val result = await(underTest.fetchAllPublicAPIs(alsoIncludePrivateTrials))

        result shouldBe List(anApiDefinition(
          context,
          publicVersion1,
          publicVersion2,
          privateTrialVersion
        ))
      }
    }
  }

  "fetchAll" should {
    "return all APIs" in new Setup {
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(List(apiDefinitionWithAllVersions, apiDefinition)))

      val result: List[ApiDefinition] = await(underTest.fetchAll)

      val expectedApiDefinitions = List(apiDefinitionWithAllVersions, apiDefinition).map(ApiDefinition.fromStored)
      result shouldBe expectedApiDefinitions
    }
  }

  "fetchAllPrivateAPIs" should {

    "return all Private API Definitions" in new Setup {

      val api = aStoredApiDefinition(
        context,
        publicVersion2,
        publicVersion1,
        privateVersion,
        privateTrialVersion
      )

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllPrivateAPIs())

      result shouldBe List(anApiDefinition(
        context,
        privateVersion,
        privateTrialVersion
      ))
    }
  }

  "delete" should {

    "delete the API in AWS and the repository" in new Setup {
      await(underTest.delete(apiDefinition.serviceName))

      verify(mockAwsApiPublisher).delete(apiDefinition)
      verify(mockAPIDefinitionRepository).delete(apiDefinition.serviceName)
    }

    "return success when the API doesnt exist" in new Setup {
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("service"))).thenReturn(successful(None))

      await(underTest.delete(ServiceName("service")))
    }

    "fail when AWS delete fails" in new Setup {
      when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(failed(new RuntimeException()))

      intercept[RuntimeException] {
        await(underTest.delete(apiDefinition.serviceName))
      }
    }

    "fail when repository delete fails" in new Setup {
      when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(failed(new RuntimeException()))

      intercept[RuntimeException] {
        await(underTest.delete(apiDefinition.serviceName))
      }
    }
  }

  "publishAllToAws" should {
    "publish all APIs and remove unused APIs" in new Setup {
      val apiDefinition1: StoredApiDefinition = someAPIDefinition
      val apiDefinition2: StoredApiDefinition = someAPIDefinition
      when(mockApiRemover.deleteUnusedApis()).thenReturn(successful(()))
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))
      when(mockAwsApiPublisher.publishAll(*)(*)).thenReturn(successful(()))

      await(underTest.publishAllToAws())

      verify(mockAwsApiPublisher, times(1)).publishAll(Seq(apiDefinition1, apiDefinition2))
    }
  }

  private def aVersion(version: ApiVersionNbr, status: ApiStatus = ApiStatus.BETA, access: ApiAccess) =
    ApiVersion(version, status, access, List(Endpoint("/test", "test", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))

  private def someAPIDefinition: StoredApiDefinition =
    StoredApiDefinition(
      serviceName,
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      context,
      List(
        ApiVersion(
          ApiVersionNbr("1.0"),
          ApiStatus.BETA,
          ApiAccess.PUBLIC,
          List(
            Endpoint(
              "/today",
              "Get Today's Date",
              HttpMethod.GET,
              AuthType.NONE,
              ResourceThrottlingTier.UNLIMITED,
              None,
              queryParameters = List.empty
            )
          )
        )
      ),
      false,
      false,
      None,
      List(ApiCategory.OTHER)
    )

}
