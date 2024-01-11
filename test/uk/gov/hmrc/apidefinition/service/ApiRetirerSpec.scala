import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import play.api.Logger
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.ApiRetirer
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec


class ApiRetirerSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val hc = HeaderCarrier()

    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockLogger: Logger       = mock[Logger]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val underTest = new ApiRetirer(mockAppConfig, mockAPIDefinitionRepository) {
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
    requiresTrust = false,
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
    requiresTrust = false,
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
    requiresTrust = false,
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
    requiresTrust = false,
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
    requiresTrust = false,
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.AGENTS)
  )

"retireApis" should {
    "fetch apis to retire and set them to retired" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(List("api1,2.0"))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))

      await(underTest.retireApis())
      verify(mockLogger).info(s"Attempting to retire 1 API versions.")
      verifyNoMoreInteractions(mockLogger)
      
      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

    "fetch multiple apis and versions and set them to retired" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(List("api1,2.0", "api2,3.0", "api2,1.0"))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api2"))).thenReturn(successful(Some(testApiDefinition2)))

      await(underTest.retireApis())
      verify(mockLogger).info(s"Attempting to retire 3 API versions.")
      verifyNoMoreInteractions(mockLogger)

      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(2)).fetchByServiceName(ServiceName("api2"))
      
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition2)
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition3)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

    "Do nothing when the list is empty" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(List())

      await(underTest.retireApis())
      verifyZeroInteractions(mockLogger)
      verifyZeroInteractions(mockAPIDefinitionRepository)
    }

    "Do nothing when the list is doesn't exist" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(null)

      await(underTest.retireApis())
      verifyZeroInteractions(mockLogger)
      verifyZeroInteractions(mockAPIDefinitionRepository)
    }

    "log an appropriate message when the api can not be found in the collection" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(List("api6,2.0"))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api6"))).thenReturn(successful(None))

      await(underTest.retireApis())
      verify(mockLogger).warn(s"api6 version 2.0 can not be found")
    }

    "ignore when api name is invalid" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(List("api1,2.0", "someInvalidFormat,", ",anotherInvalidFormat", "yetanotherInvalidFormat"))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))

      await(underTest.retireApis())
      verify(mockAPIDefinitionRepository, times(1)).fetchByServiceName(ServiceName("api1"))
      verify(mockAPIDefinitionRepository, times(1)).save(expectedApiDefinition)
      verifyNoMoreInteractions(mockAPIDefinitionRepository)
    }

  }
}