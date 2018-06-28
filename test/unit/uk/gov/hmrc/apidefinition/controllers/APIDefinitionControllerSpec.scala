/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.controllers

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers.{any, refEq, eq => isEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{verify, verifyZeroInteractions, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.apidefinition.config.ControllerConfiguration
import uk.gov.hmrc.http.HeaderNames.xRequestId
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.play.microservice.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper

import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class APIDefinitionControllerSpec extends UnitSpec
  with WithFakeApplication with ScalaFutures with MockitoSugar {

  trait Setup extends MicroserviceFilterSupport {
    val mockAPIDefinitionService = mock[APIDefinitionService]

    implicit lazy val request = FakeRequest()
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val mockControllerConfiguration = mock[ControllerConfiguration]
    when(mockControllerConfiguration.fetchByContextTtlInSeconds).thenReturn("1234")

    val apiDefinitionMapper = fakeApplication.injector.instanceOf[APIDefinitionMapper]

    val underTest = new APIDefinitionController(mockAPIDefinitionService, mockControllerConfiguration, apiDefinitionMapper)

    def theServiceWillCreateOrUpdateTheAPIDefinition = {
      when(mockAPIDefinitionService.createOrUpdate(any[APIDefinition])(any[HeaderCarrier])).thenAnswer(new Answer[Future[APIDefinition]] {
        override def answer(invocation: InvocationOnMock): Future[APIDefinition] = {
          Future.successful(invocation.getArgument(0))
        }
      })
    }
  }

  "createOrUpdate" should {

    "succeed with a 200 (ok) when payload is valid and service responds successfully" in new Setup {

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, None, Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)), Some(true))),
        requiresTrust = Some(true), None, lastPublishedAt = None)

      theServiceWillCreateOrUpdateTheAPIDefinition

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe OK

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "map legacy API statuses to new statuses before calling the service" in new Setup {

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, None, Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)), Some(true))),
        requiresTrust = Some(true), None, lastPublishedAt = None)

      theServiceWillCreateOrUpdateTheAPIDefinition

      await(underTest.createOrUpdate()(request.withBody(Json.parse(legacyCalendarApiDefinition))))

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "fail with a 422 (invalid request) when the json payload is invalid for the request" in new Setup {

      val body = """{ "invalid": "json" }"""

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(body)))

      status(result) shouldBe UNPROCESSABLE_ENTITY

      verifyZeroInteractions(mockAPIDefinitionService)
    }

    "fail with a 409 (conflict) when the context was already defined for another service " in new Setup {
      when(mockAPIDefinitionService.createOrUpdate(any[APIDefinition])(any[HeaderCarrier]))
        .thenReturn(failed(ContextAlreadyDefinedForAnotherService("calendar", "calendar-api")))

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe CONFLICT

      (jsonBodyOf(result) \ "message").as[String] shouldBe "Context is already defined for another service. It must be unique per service."
      (jsonBodyOf(result) \ "code").as[String] shouldBe "CONTEXT_ALREADY_DEFINED"
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.createOrUpdate(any[APIDefinition])(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with a 422 (Unprocessable entity) when api name is invalid" in new Setup {

      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"STABLE",
          |         "endpoints":[
          |            {
          |               "uriPattern":"/today",
          |               "endpointName":"Get Today's Date",
          |               "method":"GET",
          |               "authType":"NONE",
          |               "throttlingTier":"UNLIMITED"
          |            }
          |         ],
          |         "endpointsEnabled": true
          |      }
          |   ]
          |}""".stripMargin.replaceAll("\n", " ")

      verifyZeroInteractions(mockAPIDefinitionService)

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(body))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      (jsonBodyOf(result) \ "message").as[String] shouldBe "requirement failed: name is required"
    }

    "fail with a 422 (Unprocessable entity) when same version appear multiple times" in new Setup {

      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"STABLE",
          |         "endpoints":[
          |            {
          |               "uriPattern":"/today",
          |               "endpointName":"Get Today's Date",
          |               "method":"GET",
          |               "authType":"NONE",
          |               "throttlingTier":"UNLIMITED"
          |            }
          |         ],
          |         "endpointsEnabled": true
          |      },
          |      {
          |         "version":"1.0",
          |         "status":"STABLE",
          |         "endpoints":[
          |            {
          |               "uriPattern":"/today",
          |               "endpointName":"Get Today's Date",
          |               "method":"GET",
          |               "authType":"NONE",
          |               "throttlingTier":"UNLIMITED"
          |            }
          |         ],
          |         "endpointsEnabled": true
          |      }
          |   ]
          |}""".stripMargin.replaceAll("\n", " ")

      verifyZeroInteractions(mockAPIDefinitionService)

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(body))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      (jsonBodyOf(result) \ "message").as[String] shouldBe "requirement failed: version numbers must be unique"
    }

    "parse an API definition with PUBLIC access type" in new Setup {
      val apiDefinitionJson =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "calendar",
          |  "requiresTrust": true,
          |  "versions": [
          |  {
          |    "access" : {
          |      "type" : "PUBLIC"
          |    },
          |    "version" : "1.0",
          |    "status" : "STABLE",
          |    "endpoints": [
          |    {
          |      "uriPattern": "/today",
          |      "endpointName":"Get Today's Date",
          |      "method": "GET",
          |      "authType": "NONE",
          |      "throttlingTier": "UNLIMITED"
          |    }
          |    ],
          |    "endpointsEnabled": true
          |  }
          |  ]
          |}""".stripMargin.replaceAll("\n", " ")

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), requiresTrust = Some(true), None, None)

      theServiceWillCreateOrUpdateTheAPIDefinition

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe OK

      ((jsonBodyOf(result) \ "versions")(0) \ "access").as[APIAccess] shouldBe PublicAPIAccess()

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "parse an API definition with not defined access type should be public" in new Setup {
      val apiDefinitionJson =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "calendar",
          |  "requiresTrust": true,
          |  "versions": [
          |  {
          |    "version" : "1.0",
          |    "status" : "STABLE",
          |    "endpoints": [
          |    {
          |      "uriPattern": "/today",
          |      "endpointName":"Get Today's Date",
          |      "method": "GET",
          |      "authType": "NONE",
          |      "throttlingTier": "UNLIMITED"
          |    }
          |    ],
          |    "endpointsEnabled": true
          |  }
          |  ]
          |}""".stripMargin.replaceAll("\n", " ")

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, None,
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), requiresTrust = Some(true), None, None)

      theServiceWillCreateOrUpdateTheAPIDefinition

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe OK

      (jsonBodyOf(result) \ "versions")(0).as[APIVersion].access shouldBe None

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "parse an API definition with PRIVATE access type" in new Setup {
      val apiDefinitionJson =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "calendar",
          |  "requiresTrust": true,
          |  "versions": [
          |  {
          |    "version" : "1.0",
          |    "status" : "STABLE",
          |    "access" : {
          |      "type" : "PRIVATE",
          |      "whitelistedApplicationIds" : ["app-id-1","app-id-2"]
          |    },
          |    "endpoints": [
          |    {
          |      "uriPattern": "/today",
          |      "endpointName":"Get Today's Date",
          |      "method": "GET",
          |      "authType": "NONE",
          |      "throttlingTier": "UNLIMITED"
          |    }
          |    ],
          |    "endpointsEnabled": true
          |  }
          |  ]
          |}""".stripMargin.replaceAll("\n", " ")

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE,  Some(PrivateAPIAccess(Seq("app-id-1","app-id-2"))),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), requiresTrust = Some(true), None, None)

      theServiceWillCreateOrUpdateTheAPIDefinition

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe OK

      ((jsonBodyOf(result) \ "versions")(0) \ "access").as[APIAccess] shouldBe PrivateAPIAccess(Seq("app-id-1", "app-id-2"))

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "fail with a 422 (Unprocessable entity) when access type 'PROTECTED' is unkown" in new Setup {

      val apiDefinitionJson =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "calendar",
          |  "requiresTrust": true,
          |  "versions": [
          |  {
          |    "access" : {
          |      "type" : "PROTECTED"
          |    },
          |    "version" : "1.0",
          |    "status" : "STABLE",
          |    "endpoints": [
          |    {
          |      "uriPattern": "/today",
          |      "endpointName":"Get Today's Date",
          |      "method": "GET",
          |      "authType": "NONE",
          |      "throttlingTier": "UNLIMITED"
          |    }
          |    ]
          |  }
          |  ]
          |}""".stripMargin.replaceAll("\n", " ")

      verifyZeroInteractions(mockAPIDefinitionService)

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      (jsonBodyOf(result) \ "message").as[String] shouldBe "Json cannot be converted to API Definition"
    }

    "fail with a 422 (Unprocessable entity) when access type 'PRIVATE' does not define whitelistedApplicationIds" in new Setup {

      val apiDefinitionJson =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "calendar",
          |  "requiresTrust": true,
          |  "versions": [
          |  {
          |    "access" : {
          |      "type" : "PRIVATE"
          |    },
          |    "version" : "1.0",
          |    "status" : "STABLE",
          |    "endpoints": [
          |    {
          |      "uriPattern": "/today",
          |      "endpointName":"Get Today's Date",
          |      "method": "GET",
          |      "authType": "NONE",
          |      "throttlingTier": "UNLIMITED"
          |    }
          |    ]
          |  }
          |  ]
          |}""".stripMargin.replaceAll("\n", " ")

      verifyZeroInteractions(mockAPIDefinitionService)

      val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      (jsonBodyOf(result) \ "message").as[String] shouldBe "API Access 'PRIVATE' should define 'whitelistedApplicationIds'"
    }
  }

  "fetchExtended" should {

    def extDefinition(service: String, email: Option[String]): Option[ExtendedAPIDefinition] = {
      Some(ExtendedAPIDefinition(service, "http://calendar", "Calendar API", "My Calendar API", "calendar",
        requiresTrust = false, isTestSupport = false,
        Seq(
          ExtendedAPIVersion("1.0", APIStatus.BETA,
            Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
            Some(APIAvailability(endpointsEnabled = true, PublicAPIAccess(), email.isDefined, authorised = true)), None)
        ),
        lastPublishedAt = None
      ))
    }

    "succeed with a 200 (ok) when an API exists for the given serviceName and user has access" in new Setup {
      val serviceName = "calendar"
      val userEmail = "user@email.com"
      val extendedApiDefinition = extDefinition(serviceName, Some(userEmail))

      when(mockAPIDefinitionService.fetchExtended(isEq(serviceName), isEq(Some(userEmail)))(any[HeaderCarrier]))
        .thenReturn(successful(extendedApiDefinition))

      val result = await(underTest.fetchExtended(serviceName)(FakeRequest("GET", s"?email=$userEmail")))

      status(result) shouldBe OK
      jsonBodyOf(result).asOpt[ExtendedAPIDefinition] shouldBe extendedApiDefinition
    }

    "succeed with a 200 (ok) when an API exists for the given serviceName and no logged in user" in new Setup {

      val serviceName = "calendar"
      val userEmail = "user@email.com"
      val extendedApiDefinition = extDefinition(serviceName, None)

      when(mockAPIDefinitionService.fetchExtended(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(successful(extendedApiDefinition))

      val result = await(underTest.fetchExtended(serviceName)(FakeRequest("GET", "")))

      status(result) shouldBe OK
      jsonBodyOf(result).asOpt[ExtendedAPIDefinition] shouldBe extendedApiDefinition
    }

    "fail with a 404 (not found) when no public API exists for the given serviceName" in new Setup {

      val serviceName = "calendar"

      when(mockAPIDefinitionService.fetchExtended(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(successful(None))

      val result = await(underTest.fetchExtended(serviceName)(request))

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {

      val serviceName = "calendar"

      when(mockAPIDefinitionService.fetchExtended(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.fetchExtended(serviceName)(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "fetch" should {

    "succeed with a 200 (ok) when an API exists for the given serviceName and user" in new Setup {

      val serviceName = "calendar"
      val userEmail = "user@email.com"

      val aTime = DateTime.now(DateTimeZone.forID("Asia/Harbin"))

      val apiDefinition = APIDefinition(serviceName, "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.BETA, Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = None, lastPublishedAt = Some(aTime))

      when(mockAPIDefinitionService.fetch(isEq(serviceName), isEq(Some(userEmail)))(any[HeaderCarrier]))
        .thenReturn(successful(Some(apiDefinition)))

      val result = await(underTest.fetch(serviceName)(FakeRequest("GET", s"?email=$userEmail")))

      status(result) shouldBe OK
      Json.prettyPrint(jsonBodyOf(result)) shouldBe
        s"""{
            |  "serviceName" : "calendar",
            |  "serviceBaseUrl" : "http://calendar",
            |  "name" : "Calendar API",
            |  "description" : "My Calendar API",
            |  "context" : "calendar",
            |  "versions" : [ {
            |    "version" : "1.0",
            |    "status" : "BETA",
            |    "access" : {
            |      "type" : "PUBLIC"
            |    },
            |    "endpoints" : [ {
            |      "uriPattern" : "/today",
            |      "endpointName" : "Get Today's Date",
            |      "method" : "GET",
            |      "authType" : "NONE",
            |      "throttlingTier" : "UNLIMITED"
            |    } ],
            |    "endpointsEnabled" : true
            |  } ],
            |  "lastPublishedAt" : "${ISODateTimeFormat.dateTime().withZoneUTC().print(aTime)}"
            |}""".trim.stripMargin
    }

    "succeed with a 200 (ok) when a public API exists for the given serviceName" in new Setup {

      val serviceName = "calendar"
      val userEmail = "user@email.com"

      val apiDefinition = APIDefinition(serviceName, "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = None)

      when(mockAPIDefinitionService.fetch(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(successful(Some(apiDefinition)))

      val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe OK
    }

    "fail with a 404 (not found) when no public API exists for the given serviceName" in new Setup {

      val serviceName = "calendar"

      when(mockAPIDefinitionService.fetch(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(successful(None))

      val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 404 (not found) when no API exists for the given serviceName and user" in new Setup {

      val serviceName = "calendar"
      val userEmail = "user@email.com"

      when(mockAPIDefinitionService.fetch(isEq(serviceName), isEq(Some(userEmail)))(any[HeaderCarrier]))
        .thenReturn(successful(None))

      val result = await(underTest.fetch(serviceName)(FakeRequest("GET", s"?email=$userEmail")))

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {

      val serviceName = "calendar"

      when(mockAPIDefinitionService.fetch(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "queryDispatcher" should {

    "return all the Public APIs when no context is given" in new Setup {

      val apiDefinition1 = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = Some(true))
      val apiDefinition2 = APIDefinition("employment", "http://employment", "Employment API", "My Calendar API", "employment",
        versions = Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
          Seq(Endpoint("/history", "Get Employment History", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = Some(false))

      when(mockAPIDefinitionService.fetchAllPublicAPIs())
        .thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))

      val result = await(underTest.queryDispatcher()(request))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(Seq(apiDefinition1, apiDefinition2))
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when the fetchAllPublicAPIs throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchAllPublicAPIs())
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.queryDispatcher()(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "return all Private APIs when the type parameter is defined as private" in new Setup {
      val privateApiDefinition = APIDefinition("employment", "http://employment", "Employment API", "My Calendar API", "employment",
        Seq(APIVersion("1.0", APIStatus.BETA, Some(PrivateAPIAccess(Seq("test"))),
          Seq(Endpoint("/history", "Get Employment History", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), Some(false))

      when(mockAPIDefinitionService.fetchAllPrivateAPIs())
        .thenReturn(successful(Seq(privateApiDefinition)))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=private")))
      status (result) shouldBe OK
      jsonBodyOf (result) shouldEqual Json.toJson(Seq(privateApiDefinition))
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "return all Public APIs when no the type parameter is defined as public" in new Setup {

      val apiDefinition1 = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), Some(true))
      val apiDefinition2 = APIDefinition("employment", "http://employment", "Employment API", "My Calendar API", "employment",
        Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
          Seq(Endpoint("/history", "Get Employment History", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), Some(false))

      when(mockAPIDefinitionService.fetchAllPublicAPIs())
        .thenReturn(successful(Seq(apiDefinition1, apiDefinition2)))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=public")))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(Seq(apiDefinition1, apiDefinition2))
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when private is defined and the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchAllPrivateAPIs())
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=private")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with a 400 (bad request) when an invalid type parameter is defined" in new Setup {
      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=monoid")))
      status (result) shouldBe BAD_REQUEST
    }

    "return the API when the context is defined and an API exist for the context" in new Setup {

      val apiDefinition1 = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = Some(true))

      when(mockAPIDefinitionService.fetchByContext(isEq("calendar"))(any[HeaderCarrier]))
        .thenReturn(successful(Some(apiDefinition1)))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar")))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(apiDefinition1)
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe Some("max-age=1234")
    }

    "return 404 Not Found when the context is defined and an API does not exist for the context" in new Setup {

      when(mockAPIDefinitionService.fetchByContext(isEq("calendar"))(any[HeaderCarrier]))
        .thenReturn(successful(None))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar")))

      status(result) shouldBe NOT_FOUND
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when the context is defined and the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchByContext(isEq("calendar"))(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return all the APIs available for the application when the applicationId is defined" in new Setup {

      val apiDefinitions = Seq(anApiDefinition)

      when(mockAPIDefinitionService.fetchAllAPIsForApplication("APP_ID"))
        .thenReturn(successful(apiDefinitions))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?applicationId=APP_ID")))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(apiDefinitions)
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when the applicationId is defined and the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchAllAPIsForApplication("APP_ID"))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?applicationId=APP_ID")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "return all the APIs available for the collaborator when the email is defined" in new Setup {

      val apiDefinitions = Seq(anApiDefinition)

      when(mockAPIDefinitionService.fetchAllAPIsForCollaborator(isEq("EMAIL@EMAIL.COM"))(any[HeaderCarrier]))
        .thenReturn(successful(apiDefinitions))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?email=EMAIL@EMAIL.COM")))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(apiDefinitions)
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when the email is defined and the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchAllAPIsForCollaborator(isEq("EMAIL@EMAIL.COM"))(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?email=EMAIL@EMAIL.COM")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    def getHeader(result: Result, headerName: String) = result.header.headers.get(headerName)
  }

  "validate" should {
    "succeed with status 204 (NoContent) when the payload is valid" in new Setup {

      val result = await(underTest.validate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe NO_CONTENT
    }

    "fail with status 422 (UnprocessableEntity) when the payload is invalid" in new Setup {

      val result = await(underTest.validate()(request.withBody(Json.parse(calendarApiDefinitionMissingDescription))))

      jsonBodyOf(result) shouldEqual Json.toJson(
        ErrorResponse(ErrorCode.API_INVALID_JSON, "Json cannot be converted to API Definition",
          Some(Seq(
            FieldErrorDescription("/description", "element is missing")
          ))
        )
      )
    }
  }

  "delete" should {
    "succeed with status 204 (NoContent) when the deletion succeeds" in new Setup {

      given(underTest.apiDefinitionService.delete(isEq("service-name"))(any[HeaderCarrier]))
        .willReturn(successful(()))

      val result = await(underTest.delete("service-name")(request))

      status(result) shouldBe NO_CONTENT
    }

    "fail with status 500 when the deletion fails" in new Setup {

      given(underTest.apiDefinitionService.delete(isEq("service-name"))(any[HeaderCarrier]))
        .willReturn(failed(new RuntimeException("Something went wrong")))

      val result = await(underTest.delete("service-name")(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with status 403 when the deletion is unauthorized" in new Setup {

      given(underTest.apiDefinitionService.delete(isEq("service-name"))(any[HeaderCarrier]))
        .willReturn(failed(new UnauthorizedException("Unauthorized")))

      val result = await(underTest.delete("service-name")(request))

      status(result) shouldBe FORBIDDEN
    }
  }

  "republishAll" should {
    "succeed with status 204 when all APIs are republished" in new Setup {
      given(underTest.apiDefinitionService.publishAll()(any[HeaderCarrier]))
        .willReturn(successful(()))

      val result = await(underTest.publishAll()(request))

      status(result) shouldBe NO_CONTENT
      bodyOf(result).isEmpty shouldBe true
    }

    "fail with status 500 and return the list of APIs which failed to publish" in new Setup {
      val message = "Could not republish the following APIs to WSO2: [API-1, API-2]"

      given(underTest.apiDefinitionService.publishAll()(any[HeaderCarrier]))
        .willReturn(failed(new RuntimeException(message)))

      val result = await(underTest.publishAll()(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (jsonBodyOf(result) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
      (jsonBodyOf(result) \ "message").as[String] shouldBe message
    }
  }

  private val anApiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
    versions = Seq(APIVersion("1.0", APIStatus.BETA,  Some(PublicAPIAccess()),
      Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
      Some(true))),
    requiresTrust = Some(true))

  private val legacyCalendarApiDefinition =
    """{
      |  "serviceName": "calendar",
      |  "name": "Calendar API",
      |  "description": "My Calendar API",
      |  "serviceBaseUrl": "http://calendar",
      |  "context": "calendar",
      |  "requiresTrust": true,
      |  "versions": [
      |  {
      |    "version" : "1.0",
      |    "status" : "PUBLISHED",
      |    "endpoints": [
      |    {
      |      "uriPattern": "/today",
      |      "endpointName":"Get Today's Date",
      |      "method": "GET",
      |      "authType": "NONE",
      |      "throttlingTier": "UNLIMITED"
      |    }
      |    ]
      |  }
      |  ]
      |}""".stripMargin.replaceAll("\n", " ")

  private val calendarApiDefinition =
    """{
      |  "serviceName": "calendar",
      |  "name": "Calendar API",
      |  "description": "My Calendar API",
      |  "serviceBaseUrl": "http://calendar",
      |  "context": "calendar",
      |  "requiresTrust": true,
      |  "versions": [
      |  {
      |    "version" : "1.0",
      |    "status" : "STABLE",
      |    "endpoints": [
      |    {
      |      "uriPattern": "/today",
      |      "endpointName":"Get Today's Date",
      |      "method": "GET",
      |      "authType": "NONE",
      |      "throttlingTier": "UNLIMITED"
      |    }
      |    ],
      |    "endpointsEnabled": true
      |  }
      |  ]
      |}""".stripMargin.replaceAll("\n", " ")

  private val calendarApiDefinitionMissingDescription =
    """{
      |  "serviceName": "calendar",
      |  "name": "Calendar API",
      |  "serviceBaseUrl": "http://calendar",
      |  "context": "calendar",
      |  "versions": [
      |  {
      |    "version" : "1.0",
      |    "status" : "STABLE",
      |    "endpoints": [
      |    {
      |      "uriPattern": "/today",
      |      "endpointName":"Get Today's Date",
      |      "method": "GET",
      |      "authType": "NONE",
      |      "throttlingTier": "UNLIMITED"
      |    }
      |    ],
      |    "endpointsEnabled": true
      |  }
      |  ]
      |}""".stripMargin.replaceAll("\n", " ")

}
