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

package uk.gov.hmrc.apidefinition.service

import java.util.UUID

import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.connector.ThirdPartyApplicationConnector
import org.joda.time.DateTimeUtils._
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.ISODateTimeFormat
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{APIDefinitionService, WSO2APIPublisher}
import uk.gov.hmrc.http.HeaderNames._
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.{failed, successful}

class APIDefinitionServiceSpec extends UnitSpec
  with ScalaFutures with MockitoSugar with BeforeAndAfterAll {

  private val fixedSavingTime = ISODateTimeFormat.dateTime().withZoneUTC().parseDateTime("2014-02-09T02:27:15.145Z")

  override def beforeAll(): Unit = {
    setCurrentMillisFixed(fixedSavingTime.getMillis)
  }

  override def afterAll(): Unit = {
    setCurrentMillisSystem()
  }

  trait Setup {

    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val mockWSO2APIPublisher = mock[WSO2APIPublisher]
    val mockAPIDefinitionRepository = mock[APIDefinitionRepository]
    val mockThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockAppContext = mock[AppContext]

    val underTest = new APIDefinitionService(mockWSO2APIPublisher, mockThirdPartyApplicationConnector,
      mockAPIDefinitionRepository, mockAppContext)

    val apiDefinition = someAPIDefinition
    val apiDefinitionWithSavingTime = apiDefinition.copy(lastPublishedAt = Some(fixedSavingTime))

    when(mockAPIDefinitionRepository.fetch(apiDefinition.serviceName)).thenReturn(successful(Some(apiDefinition)))
    when(mockWSO2APIPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(successful(()))
  }

  "createOrUpdate" should {

    "create or update the API Definition in both WSO2 and the repository" in new Setup {

      when(mockWSO2APIPublisher.publish(apiDefinition)).thenReturn(successful(()))
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime))
        .thenReturn(successful(apiDefinitionWithSavingTime))
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(None))

      await(underTest.createOrUpdate(apiDefinition)) shouldBe apiDefinitionWithSavingTime

      verify(mockWSO2APIPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      verify(mockAPIDefinitionRepository, times(1)).fetchByContext(apiDefinition.context)
    }

    "propagate unexpected errors that happen when trying to publish an API" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context))
        .thenReturn(successful(None))
      when(mockWSO2APIPublisher.publish(apiDefinition))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }
      thrown.getMessage shouldBe "Something went wrong"

      verify(mockAPIDefinitionRepository, never()).save(any[APIDefinition])
      verify(mockWSO2APIPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).fetchByContext(apiDefinition.context)
    }

    "fail to create the definition in the repository if context for another service name already exists" in new Setup {
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context))
        .thenReturn(successful(Some(apiDefinition.copy(serviceName = "differentServiceName"))))

      intercept[ContextAlreadyDefinedForAnotherService] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      verify(mockAPIDefinitionRepository, times(1)).fetchByContext(apiDefinition.context)
      verifyZeroInteractions(mockWSO2APIPublisher)
    }

    "create or update the API Definition if context is defined for the same service in both WSO2 and the repository" in new Setup {

      when(mockWSO2APIPublisher.publish(apiDefinition)).thenReturn(successful(()))
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime))
        .thenReturn(successful(apiDefinitionWithSavingTime))
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context))
        .thenReturn(successful(Some(apiDefinition)))

      await(underTest.createOrUpdate(apiDefinition)) shouldBe apiDefinitionWithSavingTime

      verify(mockWSO2APIPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      verify(mockAPIDefinitionRepository, times(1)).fetchByContext(apiDefinition.context)
    }

  }

  "fetchExtended" should {
    val serviceName = "calendar"
    val appId = UUID.randomUUID()
    val otherAppId = UUID.randomUUID()

    val publicVersion = aVersion("1.0", Some(PublicAPIAccess()))
    val publicVersionAvailability = APIAvailability(publicVersion.endpointsEnabled.getOrElse(false),
      publicVersion.access.get, loggedIn = false, authorised = true)

    val privateVersion = aVersion("2.0", Some(PrivateAPIAccess(Seq(appId.toString))))
    val privateVersionAvailability = APIAvailability(privateVersion.endpointsEnabled.getOrElse(false),
      privateVersion.access.get, loggedIn = false, authorised = false)

    val noAccessDefinedVersion = aVersion("3.0", None)
    val noAccessDefinedVersionAvailability = APIAvailability(noAccessDefinedVersion.endpointsEnabled.getOrElse(false),
      PublicAPIAccess(), loggedIn = false, authorised = true)

    val privateVersionOtherApplications = aVersion("4.0", Some(PrivateAPIAccess(Seq("OTHER_APP_ID"))))
    val privateVersionOtherApplicationsAvailability = APIAvailability(privateVersionOtherApplications.endpointsEnabled.getOrElse(false),
      privateVersionOtherApplications.access.get, loggedIn = false, authorised = true)

    "return all Versions of the API Definition with information on which versions the undefined user can see." in new Setup {
      val definition = anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion)

      when(mockAPIDefinitionRepository.fetch(serviceName)).thenReturn(successful(Some(definition)))

      val exp = Some(extAPIDefinition(
        "context",
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability), None),
          ExtendedAPIVersion(privateVersion.version, privateVersion.status, privateVersion.endpoints,
            Some(privateVersionAvailability), None),
          ExtendedAPIVersion(noAccessDefinedVersion.version, noAccessDefinedVersion.status, noAccessDefinedVersion.endpoints,
            Some(noAccessDefinedVersionAvailability), None)
        )
      ))

      val response = await(underTest.fetchExtended(serviceName, None))
      response shouldBe exp
    }

    "user cannot access the private endpoint" in new Setup {
      val definition = anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion)

      when(mockAPIDefinitionRepository.fetch(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail("Bob"))
        .thenReturn(successful(Seq(Application(otherAppId, "App"))))

      val exp = Some(extAPIDefinition(
        "context",
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(privateVersion.version, privateVersion.status, privateVersion.endpoints,
            Some(privateVersionAvailability.copy(loggedIn = true, authorised = false)), None),
          ExtendedAPIVersion(noAccessDefinedVersion.version, noAccessDefinedVersion.status, noAccessDefinedVersion.endpoints,
            Some(noAccessDefinedVersionAvailability.copy(loggedIn = true, authorised = true)), None)
        )
      ))
      val response = await(underTest.fetchExtended(serviceName, Some("Bob")))
      response shouldBe exp
    }

    "user can access the private endpoint" in new Setup {
      val definition = anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion)

      when(mockAPIDefinitionRepository.fetch(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail("Bob"))
        .thenReturn(successful(Seq(Application(appId, "App"))))

      val exp = Some(extAPIDefinition(
        "context",
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(privateVersion.version, privateVersion.status, privateVersion.endpoints,
            Some(privateVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(noAccessDefinedVersion.version, noAccessDefinedVersion.status, noAccessDefinedVersion.endpoints,
            Some(noAccessDefinedVersionAvailability.copy(loggedIn = true, authorised = true)), None)
        )
      ))
      val response = await(underTest.fetchExtended(serviceName, Some("Bob")))
      response shouldBe exp
    }

    "return all versions of the API Definition with information that the logged in user can access one private version" in new Setup {
      val definition = anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion, privateVersionOtherApplications)

      when(mockAPIDefinitionRepository.fetch(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail("Bob"))
        .thenReturn(successful(Seq(Application(appId, "App"))))

      val exp = Some(extAPIDefinition(
        "context",
        Seq(
          ExtendedAPIVersion(publicVersion.version, publicVersion.status, publicVersion.endpoints,
            Some(publicVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(privateVersion.version, privateVersion.status, privateVersion.endpoints,
            Some(privateVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(noAccessDefinedVersion.version, noAccessDefinedVersion.status, noAccessDefinedVersion.endpoints,
            Some(noAccessDefinedVersionAvailability.copy(loggedIn = true, authorised = true)), None),
          ExtendedAPIVersion(privateVersionOtherApplications.version, privateVersionOtherApplications.status, privateVersionOtherApplications.endpoints,
            Some(privateVersionOtherApplicationsAvailability.copy(loggedIn = true, authorised = false)), None)
        )
      ))
      val response = await(underTest.fetchExtended(serviceName, Some("Bob")))
      response shouldBe exp
    }

    "default to public access when no access defined for an API version" in new Setup {
      val definition = anAPIDefinition("context", noAccessDefinedVersion)

      when(mockAPIDefinitionRepository.fetch(serviceName)).thenReturn(successful(Some(definition)))

      val exp = Some(extAPIDefinition(
        "context",
        Seq(
          ExtendedAPIVersion(noAccessDefinedVersion.version, noAccessDefinedVersion.status, noAccessDefinedVersion.endpoints,
            Some(noAccessDefinedVersionAvailability.copy(loggedIn = false, authorised = true)), None)
        )
      ))
      val response = await(underTest.fetchExtended(serviceName, None))
      response shouldBe exp
    }

    "populate sandbox availability when in sandbox mode" in new Setup {
      val definition = anAPIDefinition("context", noAccessDefinedVersion)

      when(mockAppContext.isSandbox).thenReturn(true)
      when(mockAPIDefinitionRepository.fetch(serviceName)).thenReturn(successful(Some(definition)))

      val exp = Some(extAPIDefinition(
        "context",
        Seq(
          ExtendedAPIVersion(noAccessDefinedVersion.version, noAccessDefinedVersion.status, noAccessDefinedVersion.endpoints,
            None, Some(noAccessDefinedVersionAvailability.copy(loggedIn = false, authorised = true)))
        )
      ))
      val response = await(underTest.fetchExtended(serviceName, None))
      response shouldBe exp
    }
  }

  "fetch" should {
    val appId = UUID.randomUUID()
    val publicVersion = aVersion("1.0", Some(PublicAPIAccess()))
    val privateVersion = aVersion("2.0", Some(PrivateAPIAccess(Seq(appId.toString))))
    val noAccessDefinedVersion = aVersion("3.0", None)
    val privateVersionOtherApplications = aVersion("4.0", Some(PrivateAPIAccess(Seq("OTHER_APP_ID"))))

    "return the public Version of the API Definition when the user is not defined" in new Setup {
      val serviceName = "calendar"

      val aTime = DateTime.now(DateTimeZone.UTC)

      val definition = anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion).
        copy(lastPublishedAt = Some(aTime))

      when(mockAPIDefinitionRepository.fetch(serviceName)).thenReturn(successful(Some(definition)))

      val response = await(underTest.fetch(serviceName, None))

      val expected = anAPIDefinition("context", publicVersion, noAccessDefinedVersion).copy(lastPublishedAt = Some(aTime))

      response shouldBe Some(expected)
    }

    "return None when the user is not defined and none of the version of the API Definition is public" in new Setup {
      val serviceName = "calendar"

      val definition = anAPIDefinition("context", privateVersion)

      when(mockAPIDefinitionRepository.fetch(serviceName)).thenReturn(successful(Some(definition)))

      val response = await(underTest.fetch(serviceName, None))

      response shouldBe None
    }

    "return the public Version of the API Definition and the private versions of the user when the user is defined" in new Setup {
      val serviceName = "calendar"

      val email = "user@email.com"
      val definition = anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion, privateVersionOtherApplications)

      when(mockAPIDefinitionRepository.fetch(serviceName))
        .thenReturn(successful(Some(definition)))
      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email))
        .thenReturn(successful(Seq(Application(appId, "App"))))

      val response = await(underTest.fetch(serviceName, Some(email)))

      response shouldBe Some(anAPIDefinition("context", publicVersion, privateVersion, noAccessDefinedVersion))
    }
  }

  "fetchAllAPIsForApplication" should {

    "filter out versions which are private for which the application is not trusted" in new Setup {
      val applicationId = "APP_ID"
      val publicVersion = aVersion("1.0", Some(PublicAPIAccess()))
      val versionWithoutAccessDefined = aVersion("2.0", None)
      val privateVersion1 = aVersion("3.0", Some(PrivateAPIAccess(Seq(applicationId))))
      val privateVersion2 = aVersion("4.0", Some(PrivateAPIAccess(Seq("OTHER_APPID"))))

      val api = anAPIDefinition("context", publicVersion, versionWithoutAccessDefined, privateVersion1, privateVersion2)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val response = await(underTest.fetchAllAPIsForApplication(applicationId))

      response shouldBe Seq(anAPIDefinition("context", publicVersion, versionWithoutAccessDefined, privateVersion1))
    }

    "filter out APIs which do not have any version available for the application" in new Setup {
      val applicationId = "APP_ID"
      val privateVersion = aVersion("4.0", Some(PrivateAPIAccess(Seq("OTHER_APPID"))))

      val api = anAPIDefinition("context", privateVersion)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val response = await(underTest.fetchAllAPIsForApplication(applicationId))

      response shouldBe Seq()
    }
  }

  "fetchAllAPIsForCollaborator" should {
    val email = "email@email.com"
    val applicationId = UUID.randomUUID()

    "filter the private APIs for which the user does not have an application whitelisted" in new Setup {

      val publicVersion = aVersion("1.0", Some(PublicAPIAccess()))
      val versionWithoutAccessDefined = aVersion("2.0", None)
      val privateVersionWithAppWhitelisted = aVersion("3.0", Some(PrivateAPIAccess(Seq(applicationId.toString))))
      val privateVersionWithoutAppWhitelisted = aVersion("4.0", Some(PrivateAPIAccess(Seq(UUID.randomUUID().toString))))
      val api = anAPIDefinition("context", publicVersion, versionWithoutAccessDefined, privateVersionWithAppWhitelisted, privateVersionWithoutAppWhitelisted)

      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email))
        .thenReturn(successful(Seq(Application(applicationId, "App"))))
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val response = await(underTest.fetchAllAPIsForCollaborator(email))

      response shouldBe Seq(anAPIDefinition("context", publicVersion, versionWithoutAccessDefined, privateVersionWithAppWhitelisted))
    }

    "filter out APIs which do not have any version available for the user" in new Setup {

      val privateVersion = aVersion("4.0", Some(PrivateAPIAccess(Seq("OTHER_APPID"))))
      val api = anAPIDefinition("context", privateVersion)

      when(mockThirdPartyApplicationConnector.fetchApplicationsByEmail(email)).thenReturn(successful(Seq()))
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val response = await(underTest.fetchAllAPIsForCollaborator(email))

      response shouldBe Seq()
    }
  }

  "fetchByContext" should {

    "return API Definition that exists in the repository" in new Setup {

      val context = "calendar"

      when(mockAPIDefinitionRepository.fetchByContext(context)).thenReturn(successful(Some(apiDefinition)))

      val response = await(underTest.fetchByContext(context))

      response shouldBe Some(apiDefinition)
    }
  }

  "fetchAllPublicAPIs" should {

    "return all Public API Definitions and filter out private versions" in new Setup {

      val publicVersion = aVersion("1.0", Some(PublicAPIAccess()))
      val versionWithoutAccessDefined = aVersion("2.0", None)
      val privateVersion = aVersion("3.0", Some(PrivateAPIAccess(Seq("APP_ID"))))
      val api = anAPIDefinition("context", publicVersion, versionWithoutAccessDefined, privateVersion)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllPublicAPIs())

      result shouldBe Seq(anAPIDefinition("context", publicVersion, versionWithoutAccessDefined))
    }

    "filter out APIs which do not have any version available for the user" in new Setup {

      val privateVersion = aVersion("4.0", Some(PrivateAPIAccess(Seq("APP_ID"))))
      val api = anAPIDefinition("context", privateVersion)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllPublicAPIs())

      result shouldBe Seq()
    }
  }

  "fetchAllPrivateAPIs" should {

    "return all Private API Definitions" in new Setup {
      val publicVersion = aVersion("1.0", Some(PublicAPIAccess()))
      val versionWithoutAccessDefined = aVersion("2.0", None)
      val privateVersion = aVersion("3.0", Some(PrivateAPIAccess(Seq("APP_ID"))))
      val api = anAPIDefinition("context", publicVersion, versionWithoutAccessDefined, privateVersion)

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllPrivateAPIs())

      result shouldBe Seq(anAPIDefinition("context", privateVersion))
    }
  }

  "delete" should {

    "delete the API in WSO2 and the repository when there is no subscriber" in new Setup {

      when(mockWSO2APIPublisher.hasSubscribers(apiDefinition)).thenReturn(successful(false))

      await(underTest.delete(apiDefinition.serviceName))

      verify(mockWSO2APIPublisher).delete(apiDefinition)
      verify(mockAPIDefinitionRepository).delete(apiDefinition.serviceName)
    }

    "fail when one API has a subscriber" in new Setup {

      when(mockWSO2APIPublisher.hasSubscribers(apiDefinition)).thenReturn(successful(true))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[UnauthorizedException]
      }
    }

    "return success when the API doesnt exist" in new Setup {

      when(mockAPIDefinitionRepository.fetch("service")).thenReturn(successful(None))

      await(underTest.delete("service"))
    }

    "fail when wso2 delete fails" in new Setup {

      when(mockWSO2APIPublisher.hasSubscribers(apiDefinition)).thenReturn(successful(false))
      when(mockWSO2APIPublisher.delete(apiDefinition)).thenReturn(failed(new RuntimeException()))

      val future = underTest.delete(apiDefinition.serviceName)

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }

    "fail when repository delete fails" in new Setup {

      when(mockWSO2APIPublisher.hasSubscribers(apiDefinition)).thenReturn(successful(false))
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

  private def anAPIDefinition(context: String, versions: APIVersion*) = {
    APIDefinition("service","http://service","name", "description", context, versions, None, None, None)
  }

  private def extAPIDefinition(context: String, versions: Seq[ExtendedAPIVersion]) = {
    ExtendedAPIDefinition("service", "http://service", "name", "description", context, requiresTrust = false, isTestSupport = false, versions, lastPublishedAt = None)
  }

  private def aVersion(version: String, access: Option[APIAccess]) = {
    APIVersion(version, APIStatus.PROTOTYPED, access, Seq(Endpoint("/test", "test", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))
  }

  private def someAPIDefinition = {
    APIDefinition(
      "calendar",
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      "calendar",
      Seq(
        APIVersion(
          "1.0",
          APIStatus.PROTOTYPED,
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

}