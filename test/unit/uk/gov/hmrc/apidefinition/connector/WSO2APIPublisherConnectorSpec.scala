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

import com.codahale.metrics.SharedMetricRegistries
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.ContentTypes.{FORM, JSON}
import play.api.http.HeaderNames._
import play.api.http.Status.OK
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.connector.WSO2APIPublisherConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class WSO2APIPublisherConnectorSpec extends UnitSpec
  with WithFakeApplication with MockitoSugar
  with ScalaFutures with BeforeAndAfterAll {

  private val stubPort = sys.env.getOrElse("WIREMOCK", "22222").toInt
  private val stubHost = "localhost"
  private val wireMockUrl = s"http://$stubHost:$stubPort"
  private val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  private val appName = "api-definition"
  private val requestId = "requestId"
  private val encodedPassword = "a%25dmin"

  trait Setup {
    SharedMetricRegistries.clear()
    WireMock.reset()
    val serviceConfig = mock[ServicesConfig]

    implicit val hc = HeaderCarrier()
      .withExtraHeaders(xRequestId -> requestId)

    val http: HttpClient = fakeApplication.injector.instanceOf[HttpClient]
    val environment: Environment = fakeApplication.injector.instanceOf[Environment]
    val runModeConfiguration: Configuration = fakeApplication.injector.instanceOf[Configuration]

    val underTest = new WSO2APIPublisherConnector(http, environment, runModeConfiguration) {
      override val username = "admin"
      override val password = "a%dmin"
      override val serviceUrl = s"$wireMockUrl/publisher/site/blocks"
    }
  }

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  "login" should {

    "log the user into WSO2 API Publisher and return the cookies" in new Setup {

      stubFor(post(urlEqualTo("/publisher/site/blocks/user/login/ajax/login.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withRequestBody(equalTo(s"action=login&username=admin&password=$encodedPassword"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false}""")
            .withHeader(SET_COOKIE, "JSESSIONID=12345")
            .withHeader(SET_COOKIE, "api-store=loadbalancercookie")
        )
      )

      await(underTest.login()) shouldBe "JSESSIONID=12345;api-store=loadbalancercookie"
    }

    "fail with RuntimeException when WSO2 AM throws an exception" in new Setup {
      assertExceptionFromWso2(underTest, s"""{}""")
    }

    "fail with RuntimeException when WSO2 AM responds with an error" in new Setup {
      assertExceptionFromWso2(underTest, s"""{"error":true}""")
    }

  }

  "doesAPIExist" should {

    "return true when the API Definition exists in WSO2" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/item-add/ajax/add.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo("action=isAPINameExist&apiName=calendar--1.0"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false,"exist":"true"}""")))

      await(underTest.doesAPIExist(cookie, someAPIDefinition)) shouldBe true
    }

    "return false when the API Definition does not exist in WSO2" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/item-add/ajax/add.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo("action=isAPINameExist&apiName=calendar--1.0"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false,"exist":"false"}""")))

      await(underTest.doesAPIExist(cookie, someAPIDefinition)) shouldBe false
    }

  }

  "createAPI" should {

    "add a new API Definition in WSO2" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/item-add/ajax/add.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo("""action=addAPI&name=calendar--1.0&visibility=public&version=1.0&description=
              |&provider=admin&endpointType=nonsecured&http_checked=&https_checked=https
              |&tags=&thumbUrl=&context=calendar
              |&tiersCollection=BRONZE_SUBSCRIPTION,SILVER_SUBSCRIPTION,GOLD_SUBSCRIPTION,PLATINUM_SUBSCRIPTION
              |&endpoint_config={"production_endpoints":{"url":"http://localhost:9000/calendar/1.0"},"sandbox_endpoints":{"url":"http://localhost:9000/calendar/1.0/sandbox"},"endpoint_type":"http"}
              |&swagger={"paths":{"/check-weather":{"get":{"responses":{"200":{"description":"OK"}},"x-auth-type":"None","x-throttling-tier":"Unlimited"}}},"info":{"title":"calendar--1.0","version":"1.0"},"swagger":"2.0"}
              |""".trim().stripMargin.replaceAll("\n", "")))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false}""")))

      await(underTest.createAPI(cookie, someAPIDefinition)) shouldBe ((): Unit)
    }

  }

  "updateAPI" should {

    "update an existing API Definition in WSO2" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/item-add/ajax/add.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo(
          """action=updateAPI&name=calendar--1.0&visibility=public&version=1.0&description=
            |&provider=admin&endpointType=nonsecured&http_checked=&https_checked=https
            |&tags=&thumbUrl=&context=calendar
            |&tiersCollection=BRONZE_SUBSCRIPTION,SILVER_SUBSCRIPTION,GOLD_SUBSCRIPTION,PLATINUM_SUBSCRIPTION
            |&endpoint_config={"production_endpoints":{"url":"http://localhost:9000/calendar/1.0"},"sandbox_endpoints":{"url":"http://localhost:9000/calendar/1.0/sandbox"},"endpoint_type":"http"}
            |&swagger={"paths":{"/check-weather":{"get":{"responses":{"200":{"description":"OK"}},"x-auth-type":"None","x-throttling-tier":"Unlimited"}}},"info":{"title":"calendar--1.0","version":"1.0"},"swagger":"2.0"}
            |""".trim().stripMargin.replaceAll("\n", "")))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false}""")))

      await(underTest.updateAPI(cookie, someAPIDefinition)) shouldBe ((): Unit)
    }

  }

  "removeAPI" should {

    "remove an existing API Definition in WSO2" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/item-add/ajax/remove.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo("action=removeAPI&name=calendar--1.0&version=1.0&provider=admin"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false}""")))

      await(underTest.removeAPI(cookie, "calendar--1.0", "1.0")) shouldBe ((): Unit)
    }

  }

  "fetchAPI" should {

    "fetch an API Definition from WSO2" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/listing/ajax/item-list.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo(s"action=getAPI&name=calendar--1.0&version=1.0&provider=admin"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody(
              """
                |{
                |  "error": false,
                |  "api": {
                |    "name": "Calendar:1.0",
                |    "version": "1.0",
                |    "description": "",
                |    "endpoint": "",
                |    "tags": "",
                |    "availableTiers": "Unlimited",
                |    "status": "PUBLISHED",
                |    "thumb": null,
                |    "context": "/calendar/1.0",
                |    "lastUpdated": "1439561031474",
                |    "subs": 3,
                |    "templates": [
                |      [
                |        "/today",
                |        "GET",
                |        "Any",
                |        "Unlimited"
                |      ]
                |    ],
                |    "sandbox": "",
                |    "tierDescs": "Allows unlimited requests",
                |    "bizOwner": "",
                |    "bizOwnerMail": "",
                |    "techOwner": "",
                |    "techOwnerMail": "",
                |    "wadl": "",
                |    "visibility": "public",
                |    "roles": "",
                |    "tenants": "",
                |    "epUsername": "",
                |    "epPassword": "",
                |    "endpointTypeSecured": "false",
                |    "provider": "admin",
                |    "transport_http": "checked",
                |    "transport_https": "",
                |    "apiStores": null,
                |    "inSequence": "",
                |    "outSequence": "",
                |    "subscriptionAvailability": "",
                |    "subscriptionTenants": "",
                |    "endpointConfig": "{\"production_endpoints\":{\"url\":\"http://localhost:9000\",\"config\":null},\"sandbox_endpoints\":{\"url\":\"http://localhost:9001\",\"config\":null},\"implementation_status\":\"managed\",\"endpoint_type\":\"http\"}",
                |    "responseCache": "Disabled",
                |    "cacheTimeout": "300",
                |    "availableTiersDisplayNames": "Unlimited",
                |    "faultSequence": "",
                |    "destinationStats": "Disabled",
                |    "resources": "[{\"http_verbs\":{\"GET\":{\"auth_type\":\"Any\",\"throttling_tier\":\"Unlimited\"}},\"url_pattern\":\"\\/today\"}]",
                |    "scopes": "[]",
                |    "isDefaultVersion": "false",
                |    "implementation": "ENDPOINT",
                |    "environments": "Production and Sandbox",
                |    "hasDefaultVersion": false,
                |    "currentDefaultVersion": null
                |  }
                |}""".stripMargin)))

      val expectedWso2Definition = WSO2APIDefinition("Calendar:1.0", "/calendar/1.0", "1.0", 3,
        WSO2EndpointConfig(Some(WSO2Endpoint("http://localhost:9000")), WSO2Endpoint("http://localhost:9001")), None)

      await(underTest.fetchAPI(cookie, "calendar--1.0", "1.0")) shouldEqual expectedWso2Definition
    }

  }

  "publishAPIStatus" should {

    "succeed" in new Setup {

      val cookie = "login-cookie-123"

      stubFor(post(urlEqualTo("/publisher/site/blocks/life-cycles/ajax/life-cycles.jag"))
        .withHeader(USER_AGENT, equalTo(appName))
        .withHeader(xRequestId, equalTo(requestId))
        .withHeader(CONTENT_TYPE, equalTo(FORM))
        .withHeader(COOKIE, equalTo(cookie))
        .withRequestBody(equalTo(
          """action=updateStatus
            |&name=calendar--1.0
            |&version=1.0
            |&provider=admin
            |&status=PUBLISHED
            |&publishToGateway=true
            |&requireResubscription=false
            |""".trim().stripMargin.replaceAll("\n", "")))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, JSON)
            .withBody("""{"error":false}""")))

      await(underTest.publishAPIStatus(cookie, someAPIDefinition, "PUBLISHED")) shouldBe ((): Unit)
    }

  }

  private def someAPIDefinition = {

    def aWSO2HttpVerbDetails = {
      WSO2HttpVerbDetails(parameters = None,
        responses = Map("200" -> WSO2Response(description = "OK")),
        `x-auth-type` = "None",
        `x-throttling-tier` = "Unlimited",
        `x-scope` = None)
    }

    WSO2APIDefinition(
      name = "calendar--1.0",
      context = "calendar",
      version = "1.0",
      subscribersCount = 0,
      endpointConfig = WSO2EndpointConfig(
        Some(WSO2Endpoint("http://localhost:9000/calendar/1.0")),
        WSO2Endpoint("http://localhost:9000/calendar/1.0/sandbox")),
      swagger = Some(
        WSO2SwaggerDetails(
          paths = Map("/check-weather" -> Map("get" -> aWSO2HttpVerbDetails)),
          info = WSO2APIInfo("calendar--1.0", "1.0"),
          `x-wso2-security` = None))
    )
  }

  private def assertExceptionFromWso2(wso2Connector: WSO2APIPublisherConnector, jsonResponse: String)(implicit headerCarrie: HeaderCarrier): Unit = {
    stubFor(post(urlEqualTo("/publisher/site/blocks/user/login/ajax/login.jag"))
      .withHeader(USER_AGENT, equalTo(appName))
      .withHeader(xRequestId, equalTo(requestId))
      .withHeader(CONTENT_TYPE, equalTo(FORM))
      .withRequestBody(equalTo(s"action=login&username=admin&password=$encodedPassword"))
      .willReturn(
        aResponse()
          .withStatus(OK)
          .withHeader(CONTENT_TYPE, JSON)
          .withBody(jsonResponse)
      )
    )

    intercept[RuntimeException] {
      await(wso2Connector.login())
    }
  }

}
