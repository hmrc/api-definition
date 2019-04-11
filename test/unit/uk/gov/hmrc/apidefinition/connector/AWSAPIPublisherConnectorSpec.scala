/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.connector

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models.{WSO2APIInfo, WSO2HttpVerbDetails, WSO2Response, WSO2SwaggerDetails}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class AWSAPIPublisherConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with ScalaFutures with BeforeAndAfterAll {

  private val stubPort = sys.env.getOrElse("WIREMOCK", "22223").toInt
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
    WireMock.reset()
    implicit val hc = HeaderCarrier()

    val http: HttpClient = fakeApplication.injector.instanceOf[HttpClient]
    val environment: Environment = fakeApplication.injector.instanceOf[Environment]
    val runModeConfiguration: Configuration = fakeApplication.injector.instanceOf[Configuration]
    val appContext: AppContext = fakeApplication.injector.instanceOf[AppContext]

    val underTest = new AWSAPIPublisherConnector(http, environment, appContext, runModeConfiguration) {
      override val serviceBaseUrl = s"$wireMockUrl/api"
    }
  }

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() {
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
      wireMockServer.verify(postRequestedFor(urlEqualTo("/api"))
        .withHeader(CONTENT_TYPE, equalTo(JSON)).withHeader("x-api-key", equalTo("EmyYrvl")))
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
      wireMockServer.verify(postRequestedFor(urlEqualTo("/api"))
        .withHeader(CONTENT_TYPE, equalTo(JSON)).withHeader("x-api-key", equalTo("EmyYrvl")))
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
