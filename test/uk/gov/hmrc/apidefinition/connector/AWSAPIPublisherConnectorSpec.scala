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

package uk.gov.hmrc.apidefinition.connector

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.{AWSAPIInfo, AWSHttpVerbDetails, AWSResponse, AWSSwaggerDetails}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class AWSAPIPublisherConnectorSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterAll {

  private val stubPort       = sys.env.getOrElse("WIREMOCK", "22223").toInt
  private val stubHost       = "localhost"
  private val wireMockUrl    = s"http://$stubHost:$stubPort"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  private val anAWSHttpVerbDetails =
    AWSHttpVerbDetails(
      parameters = None,
      responses = Map("200" -> AWSResponse(description = "OK")),
      `x-auth-type` = "None",
      `x-throttling-tier` = "Unlimited",
      `x-scope` = None
    )
  private val apiName              = "calendar--1.0"

  private val swagger =
    AWSSwaggerDetails(
      paths = Map("/check-weather" -> Map("get" -> anAWSHttpVerbDetails)),
      info = AWSAPIInfo("calendar", ApiVersionNbr("1.0"))
    )

  trait Setup {
    SharedMetricRegistries.clear()
    WireMock.reset()
    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("foo")))

    val http                                = app.injector.instanceOf[HttpClientV2]
    val environment: Environment            = app.injector.instanceOf[Environment]
    val runModeConfiguration: Configuration = app.injector.instanceOf[Configuration]
    val appContext: AppConfig               = app.injector.instanceOf[AppConfig]
    val servicesConfig                      = mock[ServicesConfig]

    val underTest: AWSAPIPublisherConnector = new AWSAPIPublisherConnector(http, environment, appContext, runModeConfiguration, servicesConfig) {
      override val serviceBaseUrl = s"$wireMockUrl/api"
    }
  }

  override def beforeAll(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  "createOrUpdateAPI" should {
    "return RequestId when an new API is created or updated" in new Setup {
      val expectedRequestId: String = UUID.randomUUID().toString

      stubFor(put(urlPathEqualTo(s"/api/$apiName"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{ "RequestId" : "$expectedRequestId" }""")
        ))

      val result: String = await(underTest.createOrUpdateAPI(apiName, swagger))

      result shouldBe expectedRequestId
      wireMockServer.verify(putRequestedFor(urlEqualTo(s"/api/$apiName"))
        .withHeader(CONTENT_TYPE, equalTo(JSON))
        .withHeader("x-api-key", equalTo("fake-api-key"))
        .withoutHeader(AUTHORIZATION))
    }

    "return 500 when creation or update of API fails" in new Setup {
      stubFor(put(urlPathEqualTo(s"/api/$apiName"))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        ))

      intercept[UpstreamErrorResponse] {
        await(underTest.createOrUpdateAPI(apiName, swagger))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "deleteAPI" should {
    "return RequestId when an API is deleted" in new Setup {
      val expectedRequestId: String = UUID.randomUUID().toString
      stubFor(delete(urlPathEqualTo(s"/api/$apiName"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{ "RequestId" : "$expectedRequestId" }""")
        ))

      val result: String = await(underTest.deleteAPI(apiName))

      result shouldBe expectedRequestId
      wireMockServer.verify(deleteRequestedFor(urlEqualTo(s"/api/$apiName"))
        .withHeader("x-api-key", equalTo("fake-api-key"))
        .withoutHeader(AUTHORIZATION))
    }

    "return 500 when deletion of API fails" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/api/$apiName"))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        ))

      intercept[UpstreamErrorResponse] {
        await(underTest.deleteAPI(apiName))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
