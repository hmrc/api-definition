package unit.uk.gov.hmrc.apidefinition.connector

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.{NOT_FOUND, OK, INTERNAL_SERVER_ERROR}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models.{WSO2APIInfo, WSO2HttpVerbDetails, WSO2Response, WSO2SwaggerDetails}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class AWSAPIPublisherConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val stubPort = sys.env.getOrElse("WIREMOCK", "22221").toInt
  private val stubHost = "localhost"
  private val wireMockUrl = s"http://$stubHost:$stubPort"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  private val aWSO2HttpVerbDetails = WSO2HttpVerbDetails(parameters = None,
    responses = Map("200" -> WSO2Response(description = "OK")),
    `x-auth-type` = "None",
    `x-throttling-tier` = "Unlimited",
    `x-scope` = None)

  private val swagger =
    WSO2SwaggerDetails(
      paths = Map("/check-weather" -> Map("get" -> aWSO2HttpVerbDetails)),
      info = WSO2APIInfo("calendar--1.0", "1.0"))

  trait Setup {
    implicit val hc = HeaderCarrier()

    val http: HttpClient = fakeApplication.injector.instanceOf[HttpClient]
    val environment: Environment = fakeApplication.injector.instanceOf[Environment]
    val runModeConfiguration: Configuration = fakeApplication.injector.instanceOf[Configuration]

    val underTest = new AWSAPIPublisherConnector(http, environment, runModeConfiguration) {
      override val serviceBaseUrl = s"$wireMockUrl/api"
    }
  }

  override def beforeEach(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterEach(): Unit = {
    wireMockServer.stop()
  }

  "createAPI" should {
    "return restApiId when an new API is created" in new Setup {
      val expectedRestAPIId = UUID.randomUUID().toString

      stubFor(post(urlPathEqualTo("/api"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{ "restApiId" : "$expectedRestAPIId" }""")))

      val result = await(underTest.createAPI(swagger))

      result shouldBe expectedRestAPIId
    }

    "return 500 id creation of API fails" in new Setup {
      stubFor(post(urlPathEqualTo("/api"))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(underTest.createAPI(swagger))
      }
    }
  }

  "updateAPI" should {
    "return restApiId when an API is updated" in new Setup {
      val awsApiId = UUID.randomUUID().toString

      stubFor(put(urlPathEqualTo(s"/api/$awsApiId"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{ "restApiId" : "$awsApiId" }""")))

      val result = await(underTest.updateAPI(awsApiId, swagger))

      result shouldBe awsApiId
    }

    "return 404 when API does not exist in AWS" in new Setup {
      val awsApiId = UUID.randomUUID().toString

      stubFor(put(urlPathEqualTo(s"/api/$awsApiId"))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)))

      intercept[NotFoundException] {
        await(underTest.updateAPI(awsApiId, swagger))
      }
    }

    "return 500 if update of API fails" in new Setup {
      val awsApiId = UUID.randomUUID().toString

      stubFor(put(urlPathEqualTo(s"/api/$awsApiId"))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(underTest.updateAPI(awsApiId, swagger))
      }
    }
  }
}
