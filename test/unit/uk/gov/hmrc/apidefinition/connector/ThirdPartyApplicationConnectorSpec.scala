/*
 * Copyright 2020 HM Revenue & Customs
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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames.USER_AGENT
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.connector.ThirdPartyApplicationConnector
import uk.gov.hmrc.apidefinition.models.Application
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class ThirdPartyApplicationConnectorSpec extends UnitSpec
  with WithFakeApplication with MockitoSugar
  with ScalaFutures with BeforeAndAfterAll {

  private val stubPort = sys.env.getOrElse("WIREMOCK", "22221").toInt
  private val stubHost = "localhost"
  private val wireMockUrl = s"http://$stubHost:$stubPort"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  private val appName = "api-definition"
  private val requestId = "requestId"

  trait Setup {
    SharedMetricRegistries.clear()
    WireMock.reset()
    implicit val hc: HeaderCarrier = HeaderCarrier()
      .withExtraHeaders(xRequestId -> requestId)

    val http: HttpClient = fakeApplication.injector.instanceOf[HttpClient]
    val environment: Environment = fakeApplication.injector.instanceOf[Environment]
    val runModeConfiguration: Configuration = fakeApplication.injector.instanceOf[Configuration]

    val underTest: ThirdPartyApplicationConnector = new ThirdPartyApplicationConnector(http, environment, runModeConfiguration) {
      override lazy val serviceUrl = s"$wireMockUrl"
    }
  }

  "fetchApplicationsByEmail" should {
    val userEmail = "john.doe+test@example.com"

    "return all the applications the user is a collaborator on" in new Setup {
      private val applications = Seq(Application(UUID.randomUUID(), "App 1"), Application(UUID.randomUUID(), "App 2"))

      stubFor(get(urlPathEqualTo("/application"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withQueryParam("emailAddress", equalTo(userEmail))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(applications).toString())))

      val result: Seq[Application] = await(underTest.fetchApplicationsByEmail(userEmail))

      result shouldBe applications
    }

    "fail when third-party-application return a status code different of 200 (OK)" in new Setup {

      stubFor(get(urlPathEqualTo("/application"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withQueryParam("emailAddress", equalTo(userEmail))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchApplicationsByEmail(userEmail))
      }
    }
  }

  "fetchSubscribers" should {
    "return all the subscribers for an API" in new Setup {
      private val context = "hello"
      private val version = "1.0"
      private val subscribers = Seq(UUID.randomUUID, UUID.randomUUID)
      stubFor(get(urlPathEqualTo(s"/apis/$context/versions/$version/subscribers"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.obj("subscribers" -> Json.toJson(subscribers)).toString)))

      val result: Seq[UUID] = await(underTest.fetchSubscribers(context, version))

      result should contain only (subscribers: _*)
    }

    "fail when third-party-application return a status code different of 200 (OK)" in new Setup {
      private val context = "hello"
      private val version = "1.0"
      stubFor(get(urlPathEqualTo(s"/apis/$context/versions/$version/subscribers"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchSubscribers(context, version))
      }
    }
  }

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

}
