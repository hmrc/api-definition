/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.service

import java.util.UUID.randomUUID

import org.joda.time.DateTimeUtils._
import org.joda.time.format.ISODateTimeFormat
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{APIDefinitionService, AwsApiPublisher, NotificationService}
import uk.gov.hmrc.http.HeaderNames._
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class APIDefinitionServiceSpec extends UnitSpec
  with ScalaFutures with MockitoSugar with BeforeAndAfterAll {

  private val context = "calendar"
  private val serviceName = "calendar-service"
  private val fixedSavingTime = ISODateTimeFormat.dateTime().withZoneUTC().parseDateTime("2014-02-09T02:27:15.145Z")

  def unitSuccess: Future[Unit] = successful{ () }

  override def beforeAll(): Unit = {
    setCurrentMillisFixed(fixedSavingTime.getMillis)
  }

  override def afterAll(): Unit = {
    setCurrentMillisSystem()
  }

  private def anAPIDefinition(context: String, versions: APIVersion*) =
    APIDefinition("service", "http://service", "name", "description", context, versions, None, None, None)

  private def extAPIDefinition(context: String, versions: Seq[ExtendedAPIVersion]) =
    ExtendedAPIDefinition(
      "service",
      "http://service",
      "name",
      "description",
      context,
      requiresTrust = false,
      isTestSupport = false,
      versions,
      lastPublishedAt = None)

  trait Setup {

    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val mockAwsApiPublisher: AwsApiPublisher = mock[AwsApiPublisher]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockNotificationService: NotificationService = mock[NotificationService]
    val mockAppContext: AppConfig = mock[AppConfig]

    val underTest = new APIDefinitionService(
      mockAwsApiPublisher,
      mockAPIDefinitionRepository,
      mockNotificationService,
      mockAppContext)

    val applicationId = randomUUID()

    val versionWithoutAccessDefined: APIVersion = aVersion(version = "1.0", access = None)
    val publicVersion = aVersion(version = "2.0", access = Some(PublicAPIAccess()))
    val privateVersionWithAppWhitelisted: APIVersion = aVersion(version = "3.0", access = Some(PrivateAPIAccess(Seq(applicationId.toString))))
    val privateVersionWithoutAppWhitelisted: APIVersion = aVersion(version = "3.1", access = Some(PrivateAPIAccess(Seq("OTHER_APP_ID"))))
    val privateTrialVersionWithWhitelist = aVersion(version = "4.0", access = Some(PrivateAPIAccess(Seq(applicationId.toString), isTrial = Some(true))))
    val privateTrialVersionWithoutWhitelist = aVersion(version = "4.1", access = Some(PrivateAPIAccess(Seq.empty, isTrial = Some(true))))

    val allVersions = Seq(
      versionWithoutAccessDefined,
      publicVersion,
      privateVersionWithAppWhitelisted,
      privateVersionWithoutAppWhitelisted,
      privateTrialVersionWithWhitelist,
      privateTrialVersionWithoutWhitelist)

    val versionWithoutAccessDefinedAvailability = APIAvailability(versionWithoutAccessDefined.endpointsEnabled.getOrElse(false),
      PublicAPIAccess(), loggedIn = false, authorised = true)
    val publicVersionAvailability = APIAvailability(
      publicVersion.endpointsEnabled.getOrElse(false), publicVersion.access.get, loggedIn = false, authorised = true)
    val privateVersionAvailability = APIAvailability(
      privateVersionWithAppWhitelisted.endpointsEnabled.getOrElse(false), privateVersionWithAppWhitelisted.access.get, loggedIn = false, authorised = false)

    val apiDefinitionWithAllVersions = anAPIDefinition(context, allVersions: _*)
    val apiDefinition = someAPIDefinition
    val apiDefinitionWithSavingTime = apiDefinition.copy(lastPublishedAt = Some(fixedSavingTime))

    when(mockAPIDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)).thenReturn(successful(Some(apiDefinition)))
    when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(successful(()))
  }

  trait FetchSetup extends Setup {
    val versions = Seq(
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
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(successful(apiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(apiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to publish an API to AWS" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to save the definition" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      verifyZeroInteractions(mockNotificationService)
    }

    "send notifications when version of API has changed status" in new Setup {
      val apiVersion = "1.0"
      val apiContext = "foo"
      val existingStatus: models.APIStatus.Value = APIStatus.ALPHA
      val updatedStatus: models.APIStatus.Value = APIStatus.BETA
      val existingAPIDefinition: APIDefinition = anAPIDefinition(apiContext, aVersion(apiVersion, existingStatus, Some(PublicAPIAccess())))
      val updatedAPIDefinition: APIDefinition = anAPIDefinition(apiContext, aVersion(apiVersion, updatedStatus, Some(PublicAPIAccess())))
      val updatedAPIDefinitionWithSavingTime: APIDefinition = updatedAPIDefinition.copy(lastPublishedAt = Some(fixedSavingTime))

      when(mockAPIDefinitionRepository.fetchByContext(apiContext)).thenReturn(Future.successful(Some(existingAPIDefinition)))
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
          val api = anAPIDefinition(context,
            publicVersion,
            versionWithoutAccessDefined,
            privateTrialVersionWithoutWhitelist,
            privateVersionWithAppWhitelisted,
            privateVersionWithoutAppWhitelisted)

          when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

          val response = await(underTest.fetchAllAPIsForApplication(applicationId.toString, alsoIncludePrivateTrials))

          response shouldBe Seq(anAPIDefinition(context, publicVersion, versionWithoutAccessDefined, privateVersionWithAppWhitelisted))
        }
      }

      "the alsoIncludePrivateTrials option is true" should {

        val alsoIncludePrivateTrials = true

        "filter out versions which are private for which the application is not whitelisted but include private trials" in new Setup {
          val api = anAPIDefinition(context,
            publicVersion,
            versionWithoutAccessDefined,
            privateTrialVersionWithoutWhitelist,
            privateVersionWithAppWhitelisted,
            privateVersionWithoutAppWhitelisted)

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
          context, versionWithoutAccessDefined, publicVersion, privateTrialVersionWithWhitelist, privateTrialVersionWithoutWhitelist))
      }
    }
  }

  "fetchAll" should {
    "return all APIs" in new Setup {
      val expectedApiDefinitions = Seq(apiDefinitionWithAllVersions, apiDefinition)
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(expectedApiDefinitions))

      val result: Seq[APIDefinition] = await(underTest.fetchAll)

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
        privateTrialVersionWithoutWhitelist)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllPrivateAPIs())

      result shouldBe Seq(anAPIDefinition(
        context, privateVersionWithAppWhitelisted, privateVersionWithoutAppWhitelisted, privateTrialVersionWithWhitelist, privateTrialVersionWithoutWhitelist))
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

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }

    "fail when repository delete fails" in new Setup {
      when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(failed(new RuntimeException()))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }
  }

  "publishAllToAws" should {
    "publish all APIs" in new Setup {
      val apiDefinition1: APIDefinition = someAPIDefinition
      val apiDefinition2: APIDefinition = someAPIDefinition
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))

      await(underTest.publishAllToAws())

      verify(mockAwsApiPublisher, times(1)).publishAll(Seq(apiDefinition1, apiDefinition2))
    }
  }

  private def aVersion(version: String, status: APIStatus = APIStatus.BETA, access: Option[APIAccess]) =
    APIVersion(version, status, access, Seq(Endpoint("/test", "test", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))

  private def someAPIDefinition: APIDefinition =
    APIDefinition(
      serviceName,
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      context,
      Seq(
        APIVersion(
          "1.0",
          APIStatus.BETA,
          Some(PublicAPIAccess()),
          Seq(
            Endpoint(
              "/today",
              "Get Today's Date",
              HttpMethod.GET,
              AuthType.NONE,
              ResourceThrottlingTier.UNLIMITED)))),
      None, None, None)

}
