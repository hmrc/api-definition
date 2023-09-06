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

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames._

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{APIDefinitionService, AwsApiPublisher, NotificationService}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock

class APIDefinitionServiceSpec extends AsyncHmrcSpec with FixedClock {

  private val context         = ApiContext("calendar")
  private val serviceName     = "calendar-service"

  def unitSuccess: Future[Unit] = successful { () }

  private def anAPIDefinition(context: ApiContext, versions: ApiVersion*) =
    ApiDefinition("service", "http://service", "name", "description", context, versions.toList, None, None, None)

  trait Setup {

    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val mockAwsApiPublisher: AwsApiPublisher                 = mock[AwsApiPublisher]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockNotificationService: NotificationService         = mock[NotificationService]
    val mockAppContext: AppConfig                            = mock[AppConfig]

    val underTest = new APIDefinitionService(
      FixedClock.clock,
      mockAwsApiPublisher,
      mockAPIDefinitionRepository,
      mockNotificationService,
      mockAppContext
    )

    val applicationId = randomUUID()

    val versionWithoutAccessDefined: ApiVersion         = aVersion(version = ApiVersionNbr("1.0"), access = None)
    val publicVersion                                   = aVersion(version = ApiVersionNbr("2.0"), access = Some(ApiAccess.PUBLIC))
    val privateVersionWithAppWhitelisted: ApiVersion    = aVersion(version = ApiVersionNbr("3.0"), access = Some(ApiAccess.Private(List(applicationId.toString))))
    val privateVersionWithoutAppWhitelisted: ApiVersion = aVersion(version = ApiVersionNbr("3.1"), access = Some(ApiAccess.Private(List("OTHER_APP_ID"))))
    val privateTrialVersionWithWhitelist                = aVersion(version = ApiVersionNbr("4.0"), access = Some(ApiAccess.Private(List(applicationId.toString), isTrial = Some(true))))
    val privateTrialVersionWithoutWhitelist             = aVersion(version = ApiVersionNbr("4.1"), access = Some(ApiAccess.Private(Nil, isTrial = Some(true))))

    val allVersions = List(
      versionWithoutAccessDefined,
      publicVersion,
      privateVersionWithAppWhitelisted,
      privateVersionWithoutAppWhitelisted,
      privateTrialVersionWithWhitelist,
      privateTrialVersionWithoutWhitelist
    )

    val versionWithoutAccessDefinedAvailability =
      ApiAvailability(versionWithoutAccessDefined.endpointsEnabled.getOrElse(false), ApiAccess.PUBLIC, loggedIn = false, authorised = true)

    val publicVersionAvailability = ApiAvailability(
      publicVersion.endpointsEnabled.getOrElse(false),
      publicVersion.access.get,
      loggedIn = false,
      authorised = true
    )

    val privateVersionAvailability = ApiAvailability(
      privateVersionWithAppWhitelisted.endpointsEnabled.getOrElse(false),
      privateVersionWithAppWhitelisted.access.get,
      loggedIn = false,
      authorised = false
    )

    val apiDefinitionWithAllVersions = anAPIDefinition(context, allVersions: _*)
    val apiDefinition                = someAPIDefinition
    val apiDefinitionWithSavingTime  = apiDefinition.copy(lastPublishedAt = Some(instant))

    when(mockAPIDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)).thenReturn(successful(Some(apiDefinition)))
    when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(successful(()))
  }

  trait FetchSetup extends Setup {

    val versions   = Seq(
      versionWithoutAccessDefined,
      publicVersion,
      privateVersionWithAppWhitelisted,
      privateVersionWithoutAppWhitelisted,
      privateTrialVersionWithWhitelist,
      privateTrialVersionWithoutWhitelist
    )
    val definition = anAPIDefinition(context, versions: _*)

    when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(definition)))
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
      val apiVersion                                        = ApiVersionNbr("1.0")
      val apiContext                                        = ApiContext("foo")
      val existingStatus: ApiStatus                         = ApiStatus.ALPHA
      val updatedStatus: ApiStatus                          = ApiStatus.BETA
      val existingAPIDefinition: ApiDefinition              = anAPIDefinition(apiContext, aVersion(apiVersion, existingStatus, Some(ApiAccess.PUBLIC)))
      val updatedAPIDefinition: ApiDefinition               = anAPIDefinition(apiContext, aVersion(apiVersion, updatedStatus, Some(ApiAccess.PUBLIC)))
      val updatedAPIDefinitionWithSavingTime: ApiDefinition = updatedAPIDefinition.copy(lastPublishedAt = Some(instant))

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

  "fetchAllAPIsForApplication" should {

    "filter out API Definition which do not have any version available for the application" in new Setup {
      val api = anAPIDefinition(context, privateVersionWithoutAppWhitelisted, privateTrialVersionWithoutWhitelist)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val response = await(underTest.fetchAllAPIsForApplication(applicationId.toString, alsoIncludePrivateTrials = false))

      response shouldBe Seq()
    }

    "allow the option of specifying alsoIncludePrivateTrials" when {

      "the alsoIncludePrivateTrials option is false" should {

        val alsoIncludePrivateTrials = false

        "filter out versions which are private for which the application is not whitelisted" in new Setup {
          val api = anAPIDefinition(
            context,
            publicVersion,
            versionWithoutAccessDefined,
            privateTrialVersionWithoutWhitelist,
            privateVersionWithAppWhitelisted,
            privateVersionWithoutAppWhitelisted
          )

          when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

          val response = await(underTest.fetchAllAPIsForApplication(applicationId.toString, alsoIncludePrivateTrials))

          response shouldBe Seq(anAPIDefinition(context, publicVersion, versionWithoutAccessDefined, privateVersionWithAppWhitelisted))
        }
      }

      "the alsoIncludePrivateTrials option is true" should {

        val alsoIncludePrivateTrials = true

        "filter out versions which are private for which the application is not whitelisted but include private trials" in new Setup {
          val api = anAPIDefinition(
            context,
            publicVersion,
            versionWithoutAccessDefined,
            privateTrialVersionWithoutWhitelist,
            privateVersionWithAppWhitelisted,
            privateVersionWithoutAppWhitelisted
          )

          when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

          val response = await(underTest.fetchAllAPIsForApplication(applicationId.toString, alsoIncludePrivateTrials))

          response shouldBe
            Seq(anAPIDefinition(context, publicVersion, versionWithoutAccessDefined, privateTrialVersionWithoutWhitelist, privateVersionWithAppWhitelisted))
        }
      }
    }
  }

  "fetchAllPublicAPIs" when {

    "the alsoIncludePrivateTrials option is false" should {

      val alsoIncludePrivateTrials = false

      "return all Public API Definitions and filter out private versions" in new Setup {

        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinitionWithAllVersions)))

        val result = await(underTest.fetchAllPublicAPIs(alsoIncludePrivateTrials))

        result shouldBe Seq(anAPIDefinition(context, versionWithoutAccessDefined, publicVersion))
      }
    }

    "the alsoIncludePrivateTrials option is true" should {

      val alsoIncludePrivateTrials = true

      "return all Public and Private Trial API Definitions" in new Setup {

        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinitionWithAllVersions)))

        val result = await(underTest.fetchAllPublicAPIs(alsoIncludePrivateTrials))

        result shouldBe Seq(anAPIDefinition(
          context,
          versionWithoutAccessDefined,
          publicVersion,
          privateTrialVersionWithWhitelist,
          privateTrialVersionWithoutWhitelist
        ))
      }
    }
  }

  "fetchAll" should {
    "return all APIs" in new Setup {
      val expectedApiDefinitions = Seq(apiDefinitionWithAllVersions, apiDefinition)
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(expectedApiDefinitions))

      val result: Seq[ApiDefinition] = await(underTest.fetchAll)

      result shouldBe expectedApiDefinitions
    }
  }

  "fetchAllPrivateAPIs" should {

    "return all Private API Definitions" in new Setup {

      val api = anAPIDefinition(
        context,
        publicVersion,
        versionWithoutAccessDefined,
        privateVersionWithAppWhitelisted,
        privateVersionWithoutAppWhitelisted,
        privateTrialVersionWithWhitelist,
        privateTrialVersionWithoutWhitelist
      )

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllPrivateAPIs())

      result shouldBe Seq(anAPIDefinition(
        context,
        privateVersionWithAppWhitelisted,
        privateVersionWithoutAppWhitelisted,
        privateTrialVersionWithWhitelist,
        privateTrialVersionWithoutWhitelist
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
      when(mockAPIDefinitionRepository.fetchByServiceName("service")).thenReturn(successful(None))

      await(underTest.delete("service"))
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
    "publish all APIs" in new Setup {
      val apiDefinition1: ApiDefinition = someAPIDefinition
      val apiDefinition2: ApiDefinition = someAPIDefinition
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))

      await(underTest.publishAllToAws())

      verify(mockAwsApiPublisher, times(1)).publishAll(Seq(apiDefinition1, apiDefinition2))
    }
  }

  private def aVersion(version: ApiVersionNbr, status: ApiStatus = ApiStatus.BETA, access: Option[ApiAccess]) =
    ApiVersion(version, status, access, List(Endpoint("/test", "test", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))

  private def someAPIDefinition: ApiDefinition =
    ApiDefinition(
      serviceName,
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      context,
      List(
        ApiVersion(
          ApiVersionNbr("1.0"),
          ApiStatus.BETA,
          Some(ApiAccess.PUBLIC),
          List(
            Endpoint(
              "/today",
              "Get Today's Date",
              HttpMethod.GET,
              AuthType.NONE,
              ResourceThrottlingTier.UNLIMITED
            )
          )
        )
      ),
      None,
      None,
      None
    )

}
