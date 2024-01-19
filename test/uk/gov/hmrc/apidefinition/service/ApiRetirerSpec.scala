/*
 * Copyright 2024 HM Revenue & Customs
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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.{failed, successful}

import play.api.Logger
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.ApiRetirer
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiRetirerSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val hc: HeaderCarrier    = HeaderCarrier()

    val mockLogger: Logger                                   = mock[Logger]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]

    val underTest = new ApiRetirer(mockAPIDefinitionRepository) {
      override val logger: Logger = mockLogger
    }
  }

  private val testApiVersion1 = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.STABLE,
    access = ApiAccess.PUBLIC,
    endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val testApiVersion2 = ApiVersion(
    versionNbr = ApiVersionNbr("2.0"),
    status = ApiStatus.STABLE,
    access = ApiAccess.PUBLIC,
    endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val testApiVersion3 = ApiVersion(
    versionNbr = ApiVersionNbr("3.0"),
    status = ApiStatus.STABLE,
    access = ApiAccess.PUBLIC,
    endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val testApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("api1"),
    serviceBaseUrl = "test.com",
    name = "Test",
    description = "This is the Test API",
    context = ApiContext("test"),
    versions = List(testApiVersion1, testApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.AGENTS)
  )

  private val testApiDefinition2 = StoredApiDefinition(
    serviceName = ServiceName("api2"),
    serviceBaseUrl = "test.com",
    name = "Test2",
    description = "This is the Test API2",
    context = ApiContext("test2"),
    versions = List(testApiVersion1, testApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.AGENTS)
  )

  private val expectedApiVersion1 = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.RETIRED,
    access = ApiAccess.PUBLIC,
    endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val expectedApiVersion2 = ApiVersion(
    versionNbr = ApiVersionNbr("2.0"),
    status = ApiStatus.RETIRED,
    access = ApiAccess.PUBLIC,
    endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val expectedApiVersion3 = ApiVersion(
    versionNbr = ApiVersionNbr("3.0"),
    status = ApiStatus.RETIRED,
    access = ApiAccess.PUBLIC,
    endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val expectedApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("api1"),
    serviceBaseUrl = "test.com",
    name = "Test",
    description = "This is the Test API",
    context = ApiContext("test"),
    versions = List(testApiVersion1, expectedApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.AGENTS)
  )

  private val expectedApiDefinition2 = StoredApiDefinition(
    serviceName = ServiceName("api2"),
    serviceBaseUrl = "test.com",
    name = "Test2",
    description = "This is the Test API2",
    context = ApiContext("test2"),
    versions = List(testApiVersion1, testApiVersion2, expectedApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.AGENTS)
  )

  private val expectedApiDefinition3 = StoredApiDefinition(
    serviceName = ServiceName("api2"),
    serviceBaseUrl = "test.com",
    name = "Test2",
    description = "This is the Test API2",
    context = ApiContext("test2"),
    versions = List(expectedApiVersion1, testApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.AGENTS)
  )
  "retireApis" should {
    "fetch a single api to retire and set it to retired" in new Setup {
      val apisToRetire = List("api1,2.0")
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).info(s"Attempting to retire 1 API versions.")
      verify(mockLogger).debug(s"api1 version 2.0 saved.")
      verifyNoMoreInteractions(mockLogger)

      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

    "fetch multiple apis and versions and set them to retired" in new Setup {
      val apisToRetire = List("api1,2.0", "api2,3.0", "api2,1.0")
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api2"))).thenReturn(successful(Some(testApiDefinition2)))

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).info(s"Attempting to retire 3 API versions.")
      verify(mockLogger).debug(s"api1 version 2.0 saved.")
      verify(mockLogger).debug(s"api2 version 3.0 saved.")
      verify(mockLogger).debug(s"api2 version 1.0 saved.")

      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(2)).fetchByServiceName(ServiceName("api2"))

      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition2)
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition3)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

    "log an appropriate message when the api can not be found in the collection" in new Setup {
      val apisToRetire = List("api6,2.0")
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api6"))).thenReturn(successful(None))

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).warn(s"api6 version 2.0 can not be found")
    }

    "ignore when api name is invalid" in new Setup {
      val apisToRetire = List("api1,2.0", "someInvalidFormat,", ",anotherInvalidFormat", "yetanotherInvalidFormat")
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))

      await(underTest.retireApis(apisToRetire))
      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

    "save unchanged api versions" in new Setup {
      val apisToRetire = List("api1,2.0")
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))

      await(underTest.retireApis(apisToRetire))
      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

    "log an appropriate message on failure" in new Setup {
      val error        = UpstreamErrorResponse.apply("error1", 500, 1)
      val apisToRetire = List("api1,2.0")
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(failed(error))

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).warn(s"api1 retire failed.", error)
    }
  }
}
