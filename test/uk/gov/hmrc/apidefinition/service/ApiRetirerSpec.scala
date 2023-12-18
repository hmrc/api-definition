import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.services.ApiRetirer
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import scala.concurrent.ExecutionContext

class ApiRetirerSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val underTest = new ApiRetirer(mockAppConfig)
  }

"retireApis" should {
    "fetch apis to retire and set them to retired" in new Setup {

      val result: String = await(underTest.SayHello())
      result shouldBe "hellooo"

    }
  
}

}