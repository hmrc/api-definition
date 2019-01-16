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

package uk.gov.hmrc.apidefinition.connector

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import uk.gov.hmrc.apidefinition.config.WSHttp
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames.USER_AGENT
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.apidefinition.models.Application
import uk.gov.hmrc.play.config.inject.DefaultServicesConfig
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class ThirdPartyApplicationConnectorSpec extends UnitSpec
  with WithFakeApplication with MockitoSugar
  with ScalaFutures with BeforeAndAfterEach {

  private val stubPort = sys.env.getOrElse("WIREMOCK", "22221").toInt
  private val stubHost = "localhost"
  private val wireMockUrl = s"http://$stubHost:$stubPort"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  private val appName = "api-definition"
  private val requestId = "requestId"

  trait Setup {
    val serviceConfig = mock[DefaultServicesConfig]
    implicit val hc = HeaderCarrier()
      .withExtraHeaders(xRequestId -> requestId)

    val http = new WSHttp {
      override val hooks = Seq()
    }

    val underTest = new ThirdPartyApplicationConnector(serviceConfig, http) {
      override lazy val serviceUrl = s"$wireMockUrl"
    }
  }

  "fetchApplicationsByEmail" should {
    val userEmail = "john.doe+test@example.com"

    "return all the applications the user is a collaborator on" in new Setup {
      val applications = Seq(Application(UUID.randomUUID(), "App 1"), Application(UUID.randomUUID(), "App 2"))

      stubFor(get(urlPathEqualTo("/application"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withQueryParam("emailAddress", equalTo(userEmail))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.toJson(applications).toString())))

      val result = await(underTest.fetchApplicationsByEmail(userEmail))

      result shouldBe applications
    }

    "fail when third-party-application return a status code different of 200 (OK)" in new Setup {

      stubFor(get(urlPathEqualTo("/application"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withQueryParam("emailAddress", equalTo(userEmail))
        .willReturn(
          aResponse()
            .withStatus(500)))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchApplicationsByEmail(userEmail))
      }
    }
  }

  override def beforeEach(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterEach(): Unit = {
    wireMockServer.stop()
  }

}
