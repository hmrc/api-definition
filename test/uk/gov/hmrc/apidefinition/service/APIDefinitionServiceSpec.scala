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

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiStatus, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames._

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.mocks.APIEventRepositoryMockModule
import uk.gov.hmrc.apidefinition.models.{ApiEventId, ApiEvents}
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{APIDefinitionService, ApiRemover, ApiRetirer, AwsApiPublisher, NotificationService}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class APIDefinitionServiceSpec extends AsyncHmrcSpec with FixedClock {

  private val context     = ApiContext("agents/calendar")
  private val serviceName = ServiceName("calendar-service")

  def unitSuccess: Future[Unit] = successful { () }

  private def aStoredApiDefinition(context: ApiContext, versions: ApiVersion*) =
    StoredApiDefinition(ServiceName("service"), "http://service", "name", "description", context, versions.toList, false, None, List(ApiCategory.OTHER))

  private def anApiDefinition(context: ApiContext, versions: ApiVersion*) =
    ApiDefinition(ServiceName("service"), "http://service", "name", "description", context, versions.map(v => v.versionNbr -> v).toMap, false, None, List(ApiCategory.OTHER))

  trait Setup extends APIEventRepositoryMockModule {

    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val mockAwsApiPublisher: AwsApiPublisher                 = mock[AwsApiPublisher]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockNotificationService: NotificationService         = mock[NotificationService]
    val mockAppConfig: AppConfig                             = mock[AppConfig]
    val mockApiRemover: ApiRemover                           = mock[ApiRemover]
    val mockApiRetirer: ApiRetirer                           = mock[ApiRetirer]

    val underTest = new APIDefinitionService(
      FixedClock.clock,
      mockAwsApiPublisher,
      mockAPIDefinitionRepository,
      APIEventRepositoryMock.aMock,
      mockApiRemover,
      mockApiRetirer,
      mockNotificationService,
      mockAppConfig
    )

    val applicationId = ApplicationId.random
    val otherAppId    = ApplicationId.random

    val publicVersion1: ApiVersion  = aVersion(version = ApiVersionNbr("1.0"), access = ApiAccessType.PUBLIC)
    val publicVersion2              = aVersion(version = ApiVersionNbr("2.0"), access = ApiAccessType.PUBLIC)
    val internalVersion: ApiVersion = aVersion(version = ApiVersionNbr("3.1"), access = ApiAccessType.INTERNAL)
    val controlledVersion           = aVersion(version = ApiVersionNbr("4.0"), access = ApiAccessType.CONTROLLED)

    val allVersions = List(
      publicVersion1,
      publicVersion2,
      internalVersion,
      controlledVersion
    )

    val publicVersion1Availability =
      ApiAvailability(publicVersion1.endpointsEnabled, ApiAccessType.PUBLIC, loggedIn = false, authorised = true)

    val publicVersionAvailability = ApiAvailability(
      publicVersion2.endpointsEnabled,
      publicVersion2.access,
      loggedIn = false,
      authorised = true
    )

    val privateVersionAvailability = ApiAvailability(
      internalVersion.endpointsEnabled,
      internalVersion.access,
      loggedIn = false,
      authorised = false
    )

    val apiDefinitionWithAllVersions = aStoredApiDefinition(context, allVersions: _*)
    val apiDefinition                = someAPIDefinition
    val apiDefinitionWithSavingTime  = apiDefinition.copy(lastPublishedAt = Some(instant))

    when(mockAPIDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)).thenReturn(successful(Some(apiDefinition)))
    when(mockAwsApiPublisher.delete(apiDefinition)).thenReturn(successful(()))
    when(mockAPIDefinitionRepository.delete(apiDefinition.serviceName)).thenReturn(successful(()))
    APIEventRepositoryMock.CreateAll.success()

  }

  trait FetchSetup extends Setup {

    val versions         = Seq(
      publicVersion1,
      publicVersion2,
      internalVersion,
      controlledVersion
    )
    val storedDefinition = aStoredApiDefinition(context, versions: _*)
    val definition       = anApiDefinition(context, versions: _*)

    when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(Some(storedDefinition)))
  }

  "validate" should {
    "fail when keys are not present" in new Setup {
      val defectiveDefinition = apiDefinition.copy(serviceName = ServiceName(""), context = ApiContext(""), serviceBaseUrl = "", name = "")

      val result = await(underTest.validate(defectiveDefinition))

      result shouldBe NonEmptyList.of(
        "Field 'serviceName' should not be empty",
        "Field 'context' should not be empty",
        "Field 'serviceBaseUrl' should not be empty",
        "Field 'name' should not be empty"
      ).invalid
    }

    "succeed when ApiDefinitionValidator succeeds" in new Setup {
      when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List.empty)
      when(mockAPIDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)).thenReturn(successful(Some(apiDefinition)))
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAPIDefinitionRepository.fetchByServiceBaseUrl(apiDefinition.serviceBaseUrl)).thenReturn(successful(Some(apiDefinition)))
      when(mockAPIDefinitionRepository.fetchByName(apiDefinition.name)).thenReturn(successful(Some(apiDefinition)))
      when(mockAPIDefinitionRepository.fetchAllByTopLevelContext(apiDefinition.context.topLevelContext())).thenReturn(successful(Nil))

      val result = await(underTest.validate(apiDefinition))

      result shouldBe Valid(apiDefinition)
    }

    "fail when ApiDefinitionValidator fails when there is a new api with an overlapping context" in new Setup {
      when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List.empty)
      when(mockAPIDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)).thenReturn(successful(None))
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(None))
      when(mockAPIDefinitionRepository.fetchByServiceBaseUrl(apiDefinition.serviceBaseUrl)).thenReturn(successful(None))
      when(mockAPIDefinitionRepository.fetchByName(apiDefinition.name)).thenReturn(successful(None))
      when(mockAPIDefinitionRepository.fetchAllByTopLevelContext(apiDefinition.context.topLevelContext())).thenReturn(successful(
        List(apiDefinition.copy(context = ApiContext("agents/calendar/events")))
      ))

      val result = await(underTest.validate(apiDefinition))

      result shouldBe NonEmptyList.of(
        "agents/calendar - Field 'context' overlaps with 'agents/calendar/events'"
      ).invalid
    }
  }

  "createOrUpdate" should {

    "create or update the API Definition in all AWS and the repository" in new Setup {
      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(successful(apiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(apiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(apiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(apiDefinitionWithSavingTime)
      // verifyZeroInteractions(mockNotificationService)
    }

    "create or update the API Definition where a new endpoint has been added" in new Setup {
      val oldEndpoints = List(
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

      val newEndpoints = List(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        ),
        Endpoint(
          "/tomorrow",
          "Get Tomorrow's Date",
          HttpMethod.GET,
          AuthType.USER,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        )
      )

      val oldApiDefinition               = multiVersionAndEndpointAPIDefinition(oldEndpoints)
      val newApiDefinition               = multiVersionAndEndpointAPIDefinition(newEndpoints)
      val newApiDefinitionWithSavingTime = newApiDefinition.copy(lastPublishedAt = Some(instant))

      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.fetchByContext(newApiDefinition.context)).thenReturn(successful(Some(oldApiDefinition)))
      when(mockAwsApiPublisher.publish(newApiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(newApiDefinitionWithSavingTime)).thenReturn(successful(newApiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(newApiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(newApiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(newApiDefinitionWithSavingTime)

      val capture = APIEventRepositoryMock.CreateAll.verifyCall()
      capture.size shouldBe 1
      capture.head.asMetaData() shouldBe ("Api Version Endpoints Added",
      List("Version: 2.0", "Endpoint: GET: /tomorrow"))
    }

    "create or update the API Definition where an endpoint has been removed" in new Setup {
      val oldEndpoints = List(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        ),
        Endpoint(
          "/yesterday",
          "Get Yesterday's Date",
          HttpMethod.GET,
          AuthType.APPLICATION,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        )
      )

      val newEndpoints = List(
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

      val oldApiDefinition               = multiVersionAndEndpointAPIDefinition(oldEndpoints)
      val newApiDefinition               = multiVersionAndEndpointAPIDefinition(newEndpoints)
      val newApiDefinitionWithSavingTime = newApiDefinition.copy(lastPublishedAt = Some(instant))

      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.fetchByContext(newApiDefinition.context)).thenReturn(successful(Some(oldApiDefinition)))
      when(mockAwsApiPublisher.publish(newApiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(newApiDefinitionWithSavingTime)).thenReturn(successful(newApiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(newApiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(newApiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(newApiDefinitionWithSavingTime)

      val capture = APIEventRepositoryMock.CreateAll.verifyCall()
      capture.size shouldBe 1
      capture.head.asMetaData() shouldBe ("Api Version Endpoints Removed",
      List("Version: 2.0", "Endpoint: GET: /yesterday"))
    }

    "create or update the API Definition with both additions and removals" in new Setup {
      val oldEndpoints = List(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        ),
        Endpoint(
          "/tomorrow",
          "Get Tomorrow's Date",
          HttpMethod.GET,
          AuthType.APPLICATION,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        )
      )

      val newEndpoints = List(
        Endpoint(
          "/nextweeks",
          "Get Next Week's Date",
          HttpMethod.POST,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        ),
        Endpoint(
          "/tomorrow",
          "Get Tomorrow's Date",
          HttpMethod.GET,
          AuthType.USER,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        ),
        Endpoint(
          "/yesterday",
          "Get Yesterday's Date",
          HttpMethod.GET,
          AuthType.APPLICATION,
          ResourceThrottlingTier.UNLIMITED,
          None,
          queryParameters = List.empty
        )
      )

      val oldApiDefinition               = multiVersionAndEndpointAPIDefinition(oldEndpoints)
      val newApiDefinition               = multiVersionAndEndpointAPIDefinition(newEndpoints)
      val newApiDefinitionWithSavingTime = newApiDefinition.copy(lastPublishedAt = Some(instant))

      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.fetchByContext(newApiDefinition.context)).thenReturn(successful(Some(oldApiDefinition)))
      when(mockAwsApiPublisher.publish(newApiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(newApiDefinitionWithSavingTime)).thenReturn(successful(newApiDefinitionWithSavingTime))

      await(underTest.createOrUpdate(newApiDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(newApiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(newApiDefinitionWithSavingTime)

      val capture = APIEventRepositoryMock.CreateAll.verifyCall()
      capture.size shouldBe 2
      capture.head.asMetaData() shouldBe ("Api Version Endpoints Added",
      List("Version: 2.0", "Endpoint: POST: /nextweeks", "Endpoint: GET: /yesterday"))
      capture.tail.head.asMetaData() shouldBe ("Api Version Endpoints Removed",
      List("Version: 2.0", "Endpoint: GET: /today"))
    }

    "propagate unexpected errors that happen when trying to publish an API to AWS" in new Setup {
      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      // verifyZeroInteractions(mockNotificationService)
    }

    "propagate unexpected errors that happen when trying to save the definition" in new Setup {
      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.fetchByContext(apiDefinition.context)).thenReturn(successful(Some(apiDefinition)))
      when(mockAwsApiPublisher.publish(apiDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(apiDefinitionWithSavingTime)).thenReturn(failed(new RuntimeException("Something went wrong")))

      val thrown = intercept[RuntimeException] {
        await(underTest.createOrUpdate(apiDefinition))
      }

      thrown.getMessage shouldBe "Something went wrong"
      // verifyZeroInteractions(mockNotificationService)
    }

    "send notifications when version of API has changed status" in new Setup {
      val apiVersion                                              = ApiVersionNbr("1.0")
      val apiContext                                              = ApiContext("foo")
      val existingStatus: ApiStatus                               = ApiStatus.ALPHA
      val updatedStatus: ApiStatus                                = ApiStatus.BETA
      val existingAPIDefinition: StoredApiDefinition              = aStoredApiDefinition(apiContext, aVersion(apiVersion, existingStatus, ApiAccessType.PUBLIC))
      val updatedAPIDefinition: StoredApiDefinition               = aStoredApiDefinition(apiContext, aVersion(apiVersion, updatedStatus, ApiAccessType.PUBLIC))
      val updatedAPIDefinitionWithSavingTime: StoredApiDefinition = updatedAPIDefinition.copy(lastPublishedAt = Some(instant))

      when(mockAPIDefinitionRepository.fetchByContext(apiContext)).thenReturn(successful(Some(existingAPIDefinition)))
      when(mockNotificationService.process(*)(*, *)).thenReturn(unitSuccess)
      when(mockAwsApiPublisher.publish(updatedAPIDefinition)).thenReturn(unitSuccess)
      when(mockAPIDefinitionRepository.save(updatedAPIDefinitionWithSavingTime)).thenReturn(successful(updatedAPIDefinitionWithSavingTime))

      await(underTest.createOrUpdate(updatedAPIDefinition))

      verify(mockAwsApiPublisher, times(1)).publish(updatedAPIDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(updatedAPIDefinitionWithSavingTime)
      verify(mockNotificationService).process(*)(*, *)

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

    "the alsoIncludeControlledApis option is false" should {

      val alsoIncludeControlledApis = false

      "return all Public API Definitions and filter out private versions" in new Setup {

        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinitionWithAllVersions)))

        val result = await(underTest.fetchAllPublicAPIs(alsoIncludeControlledApis))

        result shouldBe List(anApiDefinition(context, publicVersion1, publicVersion2))
      }
    }

    "the alsoIncludeControlledApis option is true" should {

      val alsoIncludeControlledApis = true

      "return all Public and Controlled API Definitions" in new Setup {

        when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinitionWithAllVersions)))

        val result = await(underTest.fetchAllPublicAPIs(alsoIncludeControlledApis))

        result shouldBe List(anApiDefinition(
          context,
          publicVersion1,
          publicVersion2,
          controlledVersion
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

  "fetchAllNonPublicAPIs" should {

    "return all NonPublic API Definitions" in new Setup {

      val api = aStoredApiDefinition(
        context,
        publicVersion2,
        publicVersion1,
        internalVersion,
        controlledVersion
      )

      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(api)))

      val result = await(underTest.fetchAllNonPublicAPIs())

      result shouldBe List(anApiDefinition(
        context,
        internalVersion,
        controlledVersion
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
      when(mockAppConfig.apisToRetire).thenReturn(List.empty)
      when(mockApiRemover.deleteUnusedApis()).thenReturn(successful(()))
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))
      when(mockAwsApiPublisher.publishAll(*)(*)).thenReturn(successful(()))

      await(underTest.publishAllToAws())

      verify(mockAwsApiPublisher, times(1)).publishAll(Seq(apiDefinition1, apiDefinition2))
    }

    "Do nothing when the config list of Apis to retire is empty" in new Setup {
      val apiDefinition1: StoredApiDefinition = someAPIDefinition
      val apiDefinition2: StoredApiDefinition = someAPIDefinition
      when(mockAppConfig.apisToRetire).thenReturn(List.empty)
      when(mockApiRemover.deleteUnusedApis()).thenReturn(successful(()))
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))
      when(mockAwsApiPublisher.publishAll(*)(*)).thenReturn(successful(()))

      await(underTest.publishAllToAws())
      verifyZeroInteractions(mockApiRetirer)
    }

    "Retire Apis when the config list of Apis to retire is not empty" in new Setup {
      val apiDefinition1: StoredApiDefinition = someAPIDefinition
      val apiDefinition2: StoredApiDefinition = someAPIDefinition
      val apisToRetire                        = List("api1,2.0", "api2,3.0", "api2,1.0")
      when(mockAppConfig.apisToRetire).thenReturn(List("api1,2.0", "api2,3.0", "api2,1.0"))
      when(mockApiRetirer.retireApis(apisToRetire)).thenReturn(successful(()))
      when(mockApiRemover.deleteUnusedApis()).thenReturn(successful(()))
      when(mockAPIDefinitionRepository.fetchAll()).thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))
      when(mockAwsApiPublisher.publishAll(*)(*)).thenReturn(successful(()))

      await(underTest.publishAllToAws())
      verify(mockApiRetirer, times(1)).retireApis(apisToRetire)
    }
  }

  "fetchEvents" should {
    "return API events from the repository" in new FetchSetup {
      val apiEvent = ApiEvents.ApiCreated(ApiEventId.random, "Api 123", serviceName, instant)
      APIEventRepositoryMock.FetchEvents.success(serviceName, List(apiEvent))

      val response = await(underTest.fetchEventsByServiceName(serviceName))

      response shouldBe List(apiEvent)
    }

    "return API events from the repository, excluding no change events" in new FetchSetup {
      val apiEvent = ApiEvents.ApiCreated(ApiEventId.random, "Api 123", serviceName, instant)
      APIEventRepositoryMock.FetchEvents.success(serviceName, List(apiEvent), includeNoChange = false)

      val response = await(underTest.fetchEventsByServiceName(serviceName, includeNoChange = false))

      response shouldBe List(apiEvent)
    }

    "return empty list when there are no events" in new FetchSetup {
      APIEventRepositoryMock.FetchEvents.success(serviceName, List.empty)

      val response = await(underTest.fetchEventsByServiceName(serviceName))

      response shouldBe List.empty
    }
  }

  "deleteEventsByServiceName" should {
    "delete all API events for service from the repository" in new FetchSetup {
      APIEventRepositoryMock.DeleteEvents.success(serviceName)

      await(underTest.deleteEventsByServiceName(serviceName))

      verify(APIEventRepositoryMock.aMock).deleteEvents(serviceName)
    }
  }

  private def aVersion(version: ApiVersionNbr, status: ApiStatus = ApiStatus.BETA, access: ApiAccessType) =
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
          ApiAccessType.PUBLIC,
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
      None,
      List(ApiCategory.OTHER)
    )

  private def multiVersionAndEndpointAPIDefinition(v2Endpoints: List[Endpoint]): StoredApiDefinition =
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
          ApiAccessType.PUBLIC,
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
        ),
        ApiVersion(
          ApiVersionNbr("2.0"),
          ApiStatus.BETA,
          ApiAccessType.PUBLIC,
          v2Endpoints
        )
      ),
      false,
      None,
      List(ApiCategory.OTHER)
    )
}
