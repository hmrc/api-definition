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

import play.api.Logger
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.*
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition.ServiceBaseUrl
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.Endpoint.UriPattern
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.mocks.ApiDefinitionRepositoryMockModule
import uk.gov.hmrc.apidefinition.services.ApiRetirer
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiRetirerSpec extends AsyncHmrcSpec {

  trait Setup extends ApiDefinitionRepositoryMockModule {
    given ExecutionContext = ExecutionContext.global
    given HeaderCarrier    = HeaderCarrier()

    val mockLogger: Logger = mock[Logger]

    val underTest = new ApiRetirer(ApiDefinitionRepositoryMock.aMock) {
      override val logger: Logger = mockLogger
    }
  }

  private val testApiVersion1 = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.Stable,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Endpoint.Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val testApiVersion2 = ApiVersion(
    versionNbr = ApiVersionNbr("2.0"),
    status = ApiStatus.Stable,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Endpoint.Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val testApiVersion3 = ApiVersion(
    versionNbr = ApiVersionNbr("3.0"),
    status = ApiStatus.Stable,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Endpoint.Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val testApiDefinition1 = StoredApiDefinition(
    serviceName = ServiceName("api1"),
    serviceBaseUrl = ServiceBaseUrl("test.com"),
    name = ApiDefinition.Name("Test"),
    description = ApiDefinition.Description("This is the Test API"),
    context = ApiContext("test"),
    versions = List(testApiVersion1, testApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  private val testApiDefinition2 = StoredApiDefinition(
    serviceName = ServiceName("api2"),
    serviceBaseUrl = ServiceBaseUrl("test.com"),
    name = ApiDefinition.Name("Test2"),
    description = ApiDefinition.Description("This is the Test API2"),
    context = ApiContext("test2"),
    versions = List(testApiVersion1, testApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  private val retiredApiVersion1 = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.Retired,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Endpoint.Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val retiredApiVersion2 = ApiVersion(
    versionNbr = ApiVersionNbr("2.0"),
    status = ApiStatus.Retired,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Endpoint.Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val retiredApiVersion3 = ApiVersion(
    versionNbr = ApiVersionNbr("3.0"),
    status = ApiStatus.Retired,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Endpoint.Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val expectedApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("api1"),
    serviceBaseUrl = ServiceBaseUrl("test.com"),
    name = ApiDefinition.Name("Test"),
    description = ApiDefinition.Description("This is the Test API"),
    context = ApiContext("test"),
    versions = List(testApiVersion1, retiredApiVersion2, testApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  private val expectedApiDefinition2 = StoredApiDefinition(
    serviceName = ServiceName("api2"),
    serviceBaseUrl = ServiceBaseUrl("test.com"),
    name = ApiDefinition.Name("Test2"),
    description = ApiDefinition.Description("This is the Test API2"),
    context = ApiContext("test2"),
    versions = List(retiredApiVersion1, testApiVersion2, retiredApiVersion3),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  "retireApis" should {
    "fetch a single api to retire and set it to retired" in new Setup {
      val apisToRetire = List("api1,2.0")
      ApiDefinitionRepositoryMock.FetchByServiceName.returnsFor(ServiceName("api1"))(testApiDefinition1)
      ApiDefinitionRepositoryMock.Save.returns(expectedApiDefinition)

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).info(s"Attempting to retire 1 API versions.")
      verify(mockLogger).debug(s"api1 version 2.0 saved.")
      verifyNoMoreInteractions(mockLogger)

      ApiDefinitionRepositoryMock.FetchByServiceName.verifyCalledFor(ServiceName("api1"))
      ApiDefinitionRepositoryMock.Save.verifySaved(expectedApiDefinition)
      verifyNoMoreInteractions(ApiDefinitionRepositoryMock.aMock)
    }

    "fetch multiple apis and versions and set them to retired" in new Setup {
      val apisToRetire = List("api1,2.0", "api2,3.0", "api2,1.0")

      val afterRetiring3    = testApiDefinition2.copy(versions = List(testApiVersion1, testApiVersion2, retiredApiVersion3))
      val afterRetiringBoth = afterRetiring3.copy(versions = List(retiredApiVersion1, testApiVersion2, retiredApiVersion3))

      ApiDefinitionRepositoryMock.FetchByServiceName.returnsFor(ServiceName("api1"))(testApiDefinition1)
      ApiDefinitionRepositoryMock.FetchByServiceName.returnsFor(ServiceName("api2"))(afterRetiring3)
      ApiDefinitionRepositoryMock.FetchByServiceName.returnsFor(ServiceName("api2"))(afterRetiringBoth)
      ApiDefinitionRepositoryMock.Save.returnsFor(expectedApiDefinition)(expectedApiDefinition)
      ApiDefinitionRepositoryMock.Save.returnsFor(afterRetiring3)(afterRetiring3)
      ApiDefinitionRepositoryMock.Save.returnsFor(afterRetiringBoth)(afterRetiringBoth)

      await(underTest.retireApis(apisToRetire))

      verify(mockLogger).info(s"Attempting to retire 3 API versions.")
      verify(mockLogger).debug(s"api1 version 2.0 saved.")
      verify(mockLogger).debug(s"api2 version 3.0 saved.")
      verify(mockLogger).debug(s"api2 version 1.0 saved.")

      ApiDefinitionRepositoryMock.FetchByServiceName.verifyCalledFor(ServiceName("api1"))
      ApiDefinitionRepositoryMock.FetchByServiceName.verifyCalledFor(ServiceName("api2"), times(2))

      ApiDefinitionRepositoryMock.Save.verifySaved(expectedApiDefinition)
      ApiDefinitionRepositoryMock.Save.verifySaved(expectedApiDefinition2, times(2))
    }

    "log an appropriate message when the api can not be found in the collection" in new Setup {
      val apisToRetire = List("api6,2.0")
      ApiDefinitionRepositoryMock.FetchByServiceName.returnsNoneFor(ServiceName("api6"))

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).warn(s"api6 version 2.0 can not be found")
    }

    "ignore when api name is invalid" in new Setup {
      val apisToRetire = List("api1,2.0", "someInvalidFormat,", ",anotherInvalidFormat", "yetanotherInvalidFormat")
      ApiDefinitionRepositoryMock.FetchByServiceName.returnsFor(ServiceName("api1"))(testApiDefinition1)

      await(underTest.retireApis(apisToRetire))
      ApiDefinitionRepositoryMock.FetchByServiceName.verifyCalledFor(ServiceName("api1"))
      ApiDefinitionRepositoryMock.Save.verifySaved(expectedApiDefinition)
      verifyNoMoreInteractions(ApiDefinitionRepositoryMock.aMock)
    }

    "save unchanged api versions" in new Setup {
      val apisToRetire = List("api1,2.0")
      ApiDefinitionRepositoryMock.FetchByServiceName.returnsFor(ServiceName("api1"))(testApiDefinition1)

      await(underTest.retireApis(apisToRetire))
      ApiDefinitionRepositoryMock.FetchByServiceName.verifyCalledFor(ServiceName("api1"))
      ApiDefinitionRepositoryMock.Save.verifySaved(expectedApiDefinition)
      verifyNoMoreInteractions(ApiDefinitionRepositoryMock.aMock)
    }

    "log an appropriate message on failure" in new Setup {
      val error        = RuntimeException("Bang")
      val apisToRetire = List("api1,2.0")
      ApiDefinitionRepositoryMock.FetchByServiceName.failsFor(ServiceName("api1"))(error)

      await(underTest.retireApis(apisToRetire))
      verify(mockLogger).warn(s"api1 retire failed.", error)
    }
  }
}
