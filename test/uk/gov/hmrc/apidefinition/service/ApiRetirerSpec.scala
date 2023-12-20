import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.services.ApiRetirer
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.StoredApiDefinition
import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersion
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiAccess
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.Endpoint
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.HttpMethod
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.AuthType
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ResourceThrottlingTier


class ApiRetirerSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val underTest = new ApiRetirer(mockAppConfig, mockAPIDefinitionRepository)
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

"retireApis" should {
    "fetch apis to retire and set them to retired" in new Setup {
      when(mockAppConfig.apisToRetire).thenReturn(List("api1,2.0"))
      when(mockAPIDefinitionRepository.fetchByServiceName(ServiceName("api1"))).thenReturn(successful(Some(testApiDefinition)))

      await(underTest.retireApis())
      
      val expectedApiDefinition
      mockAPIDefinitionRepository.save(expectedApiDefinition)
    }
  }
}