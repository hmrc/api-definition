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

package unit.uk.gov.hmrc.apidefinition.service

import java.util.UUID.randomUUID

import org.joda.time.DateTimeUtils._
import org.joda.time.format.ISODateTimeFormat
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ThirdPartyApplicationConnector
import uk.gov.hmrc.apidefinition.models
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{APIDefinitionService, AwsApiPublisher, NotificationService, WSO2APIPublisher}
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

    val mockWSO2APIPublisher: WSO2APIPublisher = mock[WSO2APIPublisher]
    val mockAwsApiPublisher: AwsApiPublisher = mock[AwsApiPublisher]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockNotificationService: NotificationService = mock[NotificationService]
    val mockAppContext: AppConfig = mock[AppConfig]

    val underTest = new APIDefinitionService(
      mockWSO2APIPublisher,
      mockAwsApiPublisher,
      mockThirdPartyApplicationConnector,
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
    when(mockWSO2APIPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(successful(()))
  }

  trait FetchSetup extends Setup {
    val email = "user@email.com"
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
    when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email)).thenReturn(successful(Seq(Application(applicationId, "App"))))
  }

  "createOrUpdate" should {

    "create or update the API Definition in all WSO2, AWS and the repository" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockWSO2APIPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(successful(apiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(apiDefinition))

      verify(mockWSO2APIPublisher, times(1)).publish(apiDefinition)
      verify(mockAwsApiPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      verifyZeroInteractions(mockNotificationService)
    }

    "not call WSO2 when configured to bypass it" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(successful(apiDefinitionWithSavingTime))
      when(mockAppContext.bypassWso2).thenReturn(true)

      await(underTest.createOrUpdate(apiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      verifyZeroInteractions(mockNotificationService)
      verifyZeroInteractions(mockWSO2APIPublisher)
    }

    "propagate unexpected errors that happen when trying to publish an API to WSO2" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockWSO2APIPublisher.publish(apiDefinition)).thenReturn(failed(new RuntimeException("Something went wrong")))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to publish an API to AWS" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockWSO2APIPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to save the definition" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(Future.successful(Some(apiDefinition)))
      when(mockWSO2APIPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
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
      when(mockWSO2APIPublisher.publish(updatedAPIDefinition)).thenReturn(unitSuccess)
      when(mockAwsApiPublisher.publish(updatedAPIDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(updatedAPIDefinitionWithSavingTime)).thenReturn(successful(updatedAPIDefinitionWithSavingTime))

      await(underTest.createOrUpdate(updatedAPIDefinition))

      verify(mockWSO2APIPublisher, times(1)).publish(updatedAPIDefinition)
      verify(mockAwsApiPublisher, times(1)).publish(updatedAPIDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(updatedAPIDefinitionWithSavingTime)
      verify(mockNotificationService).notifyOfStatusChange(existingAPIDefinition.name, apiVersion, existingStatus, updatedStatus)
    }
  }

  "fetchExtended" should {
    val otherAppId = randomUUID()

    val privateVersionOtherApplications = aVersion(version = "4.0", access = Some(PrivateAPIAccess(Seq("OTHER_APP_ID"))))
    val privateVersionOtherApplicationsAvailability = APIAvailability(privateVersionOtherApplications.endpointsEnabled.getOrElse(false),
      privateVersionOtherApplications.access.get, loggedIn = false, authorised = true)

    "return all Versions of the API Definition with information on which versions the undefined user can see." in new Setup {
      val definition = anAPIDefinition(context, publicVersion, privateVersionWithAppWhitelisted, versionWithoutAccessDefined)

      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(definition)))

      val exp = Some(extAPIDefinition(
        context,
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability), None),
          ExtendedAPIVersion(privateVersionWithAppWhitelisted.version, privateVersionWithAppWhitelisted.status, privateVersionWithAppWhitelisted.endpoints,
            Some(privateVersionAvailability), None),
          ExtendedAPIVersion(versionWithoutAccessDefined.version, versionWithoutAccessDefined.status, versionWithoutAccessDefined.endpoints,
            Some(versionWithoutAccessDefinedAvailability), None)
        )
      ))

      val response = await(underTest.fetchExtendedByServiceName(serviceName, None))
      response shouldBe exp
    }

    "user cannot access the private endpoint" in new Setup {
      val definition = anAPIDefinition(context, publicVersion, privateVersionWithAppWhitelisted, versionWithoutAccessDefined)

      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail("Bob"))
        .thenReturn(successful(Seq(Application(otherAppId, "App"))))

      val exp = Some(extAPIDefinition(
        context,
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(privateVersionWithAppWhitelisted.version, privateVersionWithAppWhitelisted.status, privateVersionWithAppWhitelisted.endpoints,
            Some(privateVersionAvailability.copy(loggedIn = true, authorised = false)), None),
          ExtendedAPIVersion(versionWithoutAccessDefined.version, versionWithoutAccessDefined.status, versionWithoutAccessDefined.endpoints,
            Some(versionWithoutAccessDefinedAvailability.copy(loggedIn = true, authorised = true)), None)
        )
      ))
      val response = await(underTest.fetchExtendedByServiceName(serviceName, Some("Bob")))
      response shouldBe exp
    }

    "user can access the private endpoint" in new Setup {
      val definition = anAPIDefinition(context, publicVersion, privateVersionWithAppWhitelisted, versionWithoutAccessDefined)

      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail("Bob"))
        .thenReturn(successful(Seq(Application(applicationId, "App"))))

      val exp = Some(extAPIDefinition(
        context,
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(privateVersionWithAppWhitelisted.version, privateVersionWithAppWhitelisted.status, privateVersionWithAppWhitelisted.endpoints,
            Some(privateVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(versionWithoutAccessDefined.version, versionWithoutAccessDefined.status, versionWithoutAccessDefined.endpoints,
            Some(versionWithoutAccessDefinedAvailability.copy(loggedIn = true, authorised = true)), None)
        )
      ))
      val response = await(underTest.fetchExtendedByServiceName(serviceName, Some("Bob")))
      response shouldBe exp
    }

    "return all versions of the API Definition with information that the logged in user can access one private version" in new Setup {
      val definition = anAPIDefinition(
        context, publicVersion, privateVersionWithAppWhitelisted, versionWithoutAccessDefined, privateVersionWithoutAppWhitelisted)

      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail("Bob"))
        .thenReturn(successful(Seq(Application(applicationId, "App"))))

      val exp = Some(extAPIDefinition(
        context,
        Seq(
          ExtendedAPIVersion(
            publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(
            privateVersionWithAppWhitelisted.version, privateVersionWithAppWhitelisted.status, privateVersionWithAppWhitelisted.endpoints,
            Some(privateVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(
            versionWithoutAccessDefined.version, versionWithoutAccessDefined.status, versionWithoutAccessDefined.endpoints,
            Some(versionWithoutAccessDefinedAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(
            privateVersionWithoutAppWhitelisted.version, privateVersionWithoutAppWhitelisted.status, privateVersionWithoutAppWhitelisted.endpoints,
            Some(privateVersionOtherApplicationsAvailability.copy(loggedIn = true, authorised = false)), None)
        )
      ))
      val response = await(underTest.fetchExtendedByServiceName(serviceName, Some("Bob")))
      response shouldBe exp
    }

    "default to public access when no access defined for an API version" in new Setup {
      val definition = anAPIDefinition(context, versionWithoutAccessDefined)

      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(definition)))

      val exp = Some(extAPIDefinition(
        context,
        Seq(
          ExtendedAPIVersion(versionWithoutAccessDefined.version, versionWithoutAccessDefined.status, versionWithoutAccessDefined.endpoints,
            Some(versionWithoutAccessDefinedAvailability.copy(loggedIn = false, authorised = true)), None)
        )
      ))
      val response = await(underTest.fetchExtendedByServiceName(serviceName, None))
      response shouldBe exp
    }

    "populate sandbox availability when in sandbox mode" in new Setup {
      val definition = anAPIDefinition(context, versionWithoutAccessDefined)

      when(mockAppContext.isSandbox).thenReturn(true)
      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(definition)))

      val exp = Some(extAPIDefinition(
        context,
        Seq(
          ExtendedAPIVersion(versionWithoutAccessDefined.version, versionWithoutAccessDefined.status, versionWithoutAccessDefined.endpoints,
            None, Some(versionWithoutAccessDefinedAvailability.copy(loggedIn = false, authorised = true)))
        )
      ))
      val response = await(underTest.fetchExtendedByServiceName(serviceName, None))
      response shouldBe exp
    }
  }

  "fetch" when {

    "the alsoIncludePrivateTrials option is false" should {

      val alsoIncludePrivateTrials = false

      "return just the public and 'no access' versions of the API Definition when the user is not defined" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, None, alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain allOf(publicVersion, versionWithoutAccessDefined)
      }

      "return None when the user is not defined and none of the versions of the API Definition are public or private trial" in new FetchSetup {
        override val definition = anAPIDefinition(context, privateVersionWithAppWhitelisted)

        when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(definition)))

        val response = await(underTest.fetchByServiceName(serviceName, None, alsoIncludePrivateTrials))

        response shouldBe None
      }

      "include public and 'no access' versions of the API Definition when the user is defined" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain allOf(publicVersion, versionWithoutAccessDefined)
      }

      "include private versions of the API Definition when the specified user's application is whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain(privateVersionWithAppWhitelisted)
      }

      "exclude private versions of the API Definition when the specified user's application is not whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should not contain privateVersionWithoutAppWhitelisted
      }

      "include private trial versions of the API Definition when the specified user's application is whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain(privateTrialVersionWithWhitelist)
      }

      "exclude private trial versions of the API Definition when the specified user's application is not whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should not contain privateTrialVersionWithoutWhitelist
      }
    }

    "the alsoIncludePrivateTrials option is true" should {

      val alsoIncludePrivateTrials = true

      "return the public, 'no access' and private trial versions of the API Definition when the user is not defined" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, None, alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain allOf(publicVersion, versionWithoutAccessDefined, privateTrialVersionWithoutWhitelist)
      }

      "include public and 'no access' versions of the API Definition when the user is defined" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain allOf(publicVersion, versionWithoutAccessDefined)
      }

      "include private versions of the API Definition when the specified user's application is whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain(privateVersionWithAppWhitelisted)
      }

      "exclude private versions of the API Definition when the specified user's application is not whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should not contain privateVersionWithoutAppWhitelisted
      }

      "include private trial versions of the API Definition when the specified user's application is whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain(privateTrialVersionWithWhitelist)
      }

      "include private trial versions of the API Definition when the specified user's application is not whitelisted" in new FetchSetup {

        val response = await(underTest.fetchByServiceName(serviceName, Some(email), alsoIncludePrivateTrials))

        response shouldNot be(None)
        response.get.versions should contain(privateTrialVersionWithoutWhitelist)
      }
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

  "fetchAllAPIsForCollaborator" when {
    val email = "email@email.com"

    "the alsoIncludePrivateTrials option is false" should {

      val alsoIncludePrivateTrials = false

      "filter the private APIs for which the user does not have an application whitelisted" in new Setup {

        val api: APIDefinition =
          anAPIDefinition(
            context,
            publicVersion,
            versionWithoutAccessDefined,
            privateTrialVersionWithoutWhitelist,
            privateVersionWithAppWhitelisted,
            privateVersionWithoutAppWhitelisted)

        when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email))
          .thenReturn(successful(Seq(Application(applicationId, "App"))))
        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

        val response: Seq[APIDefinition] = await(underTest.fetchAllAPIsForCollaborator(email, alsoIncludePrivateTrials))

        response shouldBe Seq(anAPIDefinition(context, publicVersion, versionWithoutAccessDefined, privateVersionWithAppWhitelisted))
      }

      "filter out APIs which do not have any version available for the user" in new Setup {

        val api = anAPIDefinition(context, privateVersionWithAppWhitelisted, privateTrialVersionWithWhitelist)

        when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email)).thenReturn(successful(Seq.empty))
        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

        val response = await(underTest.fetchAllAPIsForCollaborator(email, alsoIncludePrivateTrials))

        response shouldBe Seq()
      }
    }

    "the alsoIncludePrivateTrials option is true" should {

      val alsoIncludePrivateTrials = true

      "filter the private APIs for which the user does not have an application whitelisted but include any private trials" in new FetchSetup {

        val api: APIDefinition =
          anAPIDefinition(
            context,
            publicVersion,
            versionWithoutAccessDefined,
            privateTrialVersionWithoutWhitelist,
            privateVersionWithAppWhitelisted,
            privateVersionWithoutAppWhitelisted)

        when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email))
          .thenReturn(successful(Seq(Application(applicationId, "App"))))
        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

        val response: Seq[APIDefinition] = await(underTest.fetchAllAPIsForCollaborator(email, alsoIncludePrivateTrials))

        response shouldBe
          Seq(anAPIDefinition(context, publicVersion, versionWithoutAccessDefined, privateTrialVersionWithoutWhitelist, privateVersionWithAppWhitelisted))
      }
    }
  }

  "fetchByContext" should {

    "return API Definition that exists in the repository" in new Setup {

      when(mockAPIDefinitionRepository.fetchByContext(context)).thenReturn(successful(Some(apiDefinition)))

      val response = await(underTest.fetchByContext(context))

      response shouldBe Some(apiDefinition)
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

    "delete the API in WSO2, AWS and the repository when there is no subscribers" in new Setup {
      when(mockThirdPartyApplicationConnector.fetchSubscribers(context, "1.0")).thenReturn(successful(Seq()))

      await(underTest.delete(apiDefinition.serviceName))

      verify(mockWSO2APIPublisher).delete(apiDefinition)
      verify(mockAwsApiPublisher).delete(apiDefinition)
      verify(mockAPIDefinitionRepository).delete(apiDefinition.serviceName)
    }

    "not call WSO2 when configured to bypass it" in new Setup {
      when(mockThirdPartyApplicationConnector.fetchSubscribers(context, "1.0")).thenReturn(successful(Seq()))
      when(mockAppContext.bypassWso2).thenReturn(true)

      await(underTest.delete(apiDefinition.serviceName))

      verify(mockAwsApiPublisher).delete(apiDefinition)
      verify(mockAPIDefinitionRepository).delete(apiDefinition.serviceName)
      verifyZeroInteractions(mockWSO2APIPublisher)
    }

    "fail when one API has a subscriber" in new Setup {
      when(mockThirdPartyApplicationConnector.fetchSubscribers(context, "1.0")).thenReturn(successful(Seq(randomUUID)))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[UnauthorizedException]
      }
    }

    "return success when the API doesnt exist" in new Setup {
      when(mockAPIDefinitionRepository.fetchByServiceName("service")).thenReturn(successful(None))

      await(underTest.delete("service"))
    }

    "fail when wso2 delete fails" in new Setup {
      when(mockThirdPartyApplicationConnector.fetchSubscribers(context, "1.0")).thenReturn(successful(Seq()))
      when(mockWSO2APIPublisher.delete(apiDefinition)).thenReturn(failed(new RuntimeException()))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }

    "fail when AWS delete fails" in new Setup {
      when(mockThirdPartyApplicationConnector.fetchSubscribers(context, "1.0")).thenReturn(successful(Seq()))
      when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(failed(new RuntimeException()))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }

    "fail when repository delete fails" in new Setup {
      when(mockThirdPartyApplicationConnector.fetchSubscribers(context, "1.0")).thenReturn(successful(Seq()))
      when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(failed(new RuntimeException()))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }
  }

  "publishAll" should {

    "publish all APIs" in new Setup {
      val apiDefinition1 = someAPIDefinition
      val apiDefinition2 = someAPIDefinition

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))
      when(mockWSO2APIPublisher.publish(Seq(apiDefinition1, apiDefinition2))).thenReturn(successful(Seq()))

      await(underTest.publishAll()) shouldBe ((): Unit)

      verify(mockWSO2APIPublisher, times(1)).publish(Seq(apiDefinition1, apiDefinition2))
    }

    "attempt to publish all APIs and report failures" in new Setup {
      val apiDefinition1 = someAPIDefinition.copy(name = "API-1")
      val apiDefinition2 = someAPIDefinition.copy(name = "API-2")
      val apiDefinition3 = someAPIDefinition.copy(name = "API-3")

      when(mockAPIDefinitionRepository.fetchAll())
        .thenReturn(successful(Seq(apiDefinition1, apiDefinition2, apiDefinition3)))
      when(mockWSO2APIPublisher.publish(Seq(apiDefinition1, apiDefinition2, apiDefinition3)))
        .thenReturn(successful(Seq(apiDefinition1.name, apiDefinition2.name)))

      val future = underTest.publishAll()

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
        ex.getMessage shouldBe s"Could not republish the following APIs to WSO2: [${apiDefinition1.name}, ${apiDefinition2.name}]"
      }

      verify(mockWSO2APIPublisher, times(1)).publish(Seq(apiDefinition1, apiDefinition2, apiDefinition3))
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
