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

package unit.uk.gov.hmrc.apidefinition.controllers

import akka.stream.Materializer
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers.{any, refEq, eq => isEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{verify, verifyZeroInteractions, when}
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.controllers.{APIDefinitionController, QueryOptions}
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper
import uk.gov.hmrc.apidefinition.validators._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class APIDefinitionControllerSpec extends UnitSpec
  with WithFakeApplication with ScalaFutures with MockitoSugar {

  trait Setup {
    implicit lazy val materializer: Materializer = fakeApplication.materializer

    implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val serviceName = "calendar"
    val userEmail = "user@email.com"

    val mockAPIDefinitionService: APIDefinitionService = mock[APIDefinitionService]
    val mockApiDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]

    val apiContextValidator: ApiContextValidator = new ApiContextValidator(mockAPIDefinitionService, mockApiDefinitionRepository)
    val queryParameterValidator: QueryParameterValidator = new QueryParameterValidator()
    val apiEndpointValidator: ApiEndpointValidator = new ApiEndpointValidator(queryParameterValidator)
    val apiVersionValidator: ApiVersionValidator = new ApiVersionValidator(apiEndpointValidator)
    val apiDefinitionValidator: ApiDefinitionValidator = new ApiDefinitionValidator(mockAPIDefinitionService, apiContextValidator, apiVersionValidator)

    val mockAppContext: AppConfig = mock[AppConfig]
    when(mockAppContext.fetchByContextTtlInSeconds).thenReturn("1234")

    val apiDefinitionMapper: APIDefinitionMapper = fakeApplication.injector.instanceOf[APIDefinitionMapper]

    val underTest = new APIDefinitionController(apiDefinitionValidator, mockAPIDefinitionService, apiDefinitionMapper, mockAppContext)
  }

  trait QueryDispatcherSetup extends Setup {

    val apiDefinitions: Seq[APIDefinition] =
      Array.fill(2)(APIDefinition("MyApiDefinitionServiceName1", "MyUrl", "MyName", "My description", "MyContext",
        Seq.empty, None))

    when(mockAPIDefinitionService.fetchByContext(any[String])).thenReturn(successful(Some(apiDefinitions.head)))
    when(mockAPIDefinitionService.fetchAllPublicAPIs(any())).thenReturn(successful(apiDefinitions))
    when(mockAPIDefinitionService.fetchAllPrivateAPIs()).thenReturn(successful(apiDefinitions))
    when(mockAPIDefinitionService.fetchAllAPIsForApplication(any(), any())).thenReturn(successful(apiDefinitions))
    when(mockAPIDefinitionService.fetchAllAPIsForCollaborator(any(), any())(any[HeaderCarrier])).thenReturn(apiDefinitions)

    def getHeader(result: Result, headerName: String) = result.header.headers.get(headerName)

    def verifyApiDefinitionsReturnedOkWithNoCacheControl(result: Result) = {
      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(apiDefinitions)
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }
  }

  trait ValidatorSetup extends Setup {
    when(mockAPIDefinitionService.fetchByContext(any[String])).thenReturn(successful(None))
    when(mockAPIDefinitionService.fetchByName(any[String])).thenReturn(successful(None))
    when(mockAPIDefinitionService.fetchByServiceBaseUrl(any[String])).thenReturn(successful(None))
    when(mockApiDefinitionRepository.fetchByServiceName(any[String])).thenReturn(successful(None))

    def theServiceWillCreateOrUpdateTheAPIDefinition: OngoingStubbing[Future[Unit]] = {
      when(mockAPIDefinitionService.createOrUpdate(any[APIDefinition])(any[HeaderCarrier])).thenReturn(Future.successful(()))
    }

    def thereAreNoOverlappingAPIContexts: OngoingStubbing[Future[Seq[APIDefinition]]] =
      when(mockApiDefinitionRepository.fetchAllByTopLevelContext(any[String])).thenReturn(Future.successful(Seq.empty))
  }

  "createOrUpdate" should {

    "succeed with a 204 (NO CONTENT) when payload is valid and service responds successfully" in new ValidatorSetup {

      val apiDefinition =
        APIDefinition(
          "calendar",
          "http://calendar",
          "Calendar API",
          "My Calendar API",
          "individuals/calendar",
          versions =
            Seq(
              APIVersion(
                "1.0",
                APIStatus.STABLE,
                None,
                Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
                Some(true))),
          requiresTrust = Some(true),
          None,
          lastPublishedAt = None)

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      val result: Result = await(underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe NO_CONTENT

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "map legacy API statuses to new statuses before calling the service" in new ValidatorSetup {

      val apiDefinition =
        APIDefinition(
          "calendar",
          "http://calendar",
          "Calendar API",
          "My Calendar API",
          "individuals/calendar",
          versions =
            Seq(
              APIVersion(
                "1.0",
                APIStatus.STABLE,
                None,
                Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
                Some(true))),
          requiresTrust = Some(true),
          None,
          lastPublishedAt = None)

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      await(underTest.createOrUpdate()(request.withBody(Json.parse(legacyCalendarApiDefinition))))

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "fail with a 422 (invalid request) when the json payload is invalid for the request" in new ValidatorSetup {

      val body = """{ "invalid": "json" }"""

      val result: Future[Result] = underTest.createOrUpdate()(request.withBody(Json.parse(body)))

      status(result) shouldBe UNPROCESSABLE_ENTITY

      verifyZeroInteractions(mockAPIDefinitionService)
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new ValidatorSetup {

      thereAreNoOverlappingAPIContexts
      when(mockAPIDefinitionService.createOrUpdate(any[APIDefinition])(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      val result: Result = await(underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with a 422 (Un-processable entity) when api name is invalid" in new ValidatorSetup {

      val body: String =
        """{
          |   "serviceName":"calendar",
          |   "name":"",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"individuals/calendar",
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

      thereAreNoOverlappingAPIContexts
      verifyZeroInteractions(mockAPIDefinitionService)

      val result: Result = await(underTest.createOrUpdate()(request.withBody(Json.parse(body))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      jsonBodyOf(result).as[ValidationErrors] shouldBe
        ValidationErrors(INVALID_REQUEST_PAYLOAD, List("Field 'name' should not be empty for API with service name 'calendar'"))
    }

    "fail with a 422 (Unprocessable entity) when same version appear multiple times" in new ValidatorSetup {

      val body: String =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"individuals/calendar",
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

      thereAreNoOverlappingAPIContexts
      verifyZeroInteractions(mockAPIDefinitionService)

      val result: Result = await(underTest.createOrUpdate()(request.withBody(Json.parse(body))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      jsonBodyOf(result).as[ValidationErrors] shouldBe
        ValidationErrors(INVALID_REQUEST_PAYLOAD, List("Field 'version' must be unique for API 'Calendar API'"))
    }

    "parse an API definition with PUBLIC access type" in new ValidatorSetup {
      val apiDefinitionJson: String =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "individuals/calendar",
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

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "individuals/calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), requiresTrust = Some(true), None, None)

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      val result: Result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe NO_CONTENT

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "parse an API definition with not defined access type should be public" in new ValidatorSetup {
      val apiDefinitionJson: String =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "individuals/calendar",
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

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "individuals/calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, None,
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), requiresTrust = Some(true), None, None)

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      private val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe NO_CONTENT

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "parse an API definition with PRIVATE access type" in new ValidatorSetup {
      private val apiDefinitionJson =
        """{
          |  "serviceName": "calendar",
          |  "name": "Calendar API",
          |  "description": "My Calendar API",
          |  "serviceBaseUrl": "http://calendar",
          |  "context": "individuals/calendar",
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

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "individuals/calendar",
        versions = Seq(APIVersion("1.0", APIStatus.STABLE, Some(PrivateAPIAccess(Seq("app-id-1", "app-id-2"))),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))), requiresTrust = Some(true), None, None)

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      private val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe NO_CONTENT

      verify(mockAPIDefinitionService).createOrUpdate(refEq(apiDefinition))(any[HeaderCarrier])
    }

    "fail with a 422 (Unprocessable entity) when access type 'PROTECTED' is unkown" in new ValidatorSetup {

      private val apiDefinitionJson =
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

      private val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      (jsonBodyOf(result) \ "message").as[String] shouldBe "Json cannot be converted to API Definition"
    }

    "fail with a 422 (Unprocessable entity) when access type 'PRIVATE' does not define whitelistedApplicationIds" in new ValidatorSetup {

      private val apiDefinitionJson =
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

      private val result = await(underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson))))

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

      private val extendedApiDefinition = extDefinition(serviceName, Some(userEmail))

      when(mockAPIDefinitionService.fetchExtendedByServiceName(isEq(serviceName), isEq(Some(userEmail)))(any[HeaderCarrier]))
        .thenReturn(successful(extendedApiDefinition))

      private val result = await(underTest.fetchExtended(serviceName)(FakeRequest("GET", s"?email=$userEmail")))

      status(result) shouldBe OK
      jsonBodyOf(result).asOpt[ExtendedAPIDefinition] shouldBe extendedApiDefinition
    }

    "succeed with a 200 (ok) when an API exists for the given serviceName and no logged in user" in new Setup {

      private val extendedApiDefinition = extDefinition(serviceName, None)

      when(mockAPIDefinitionService.fetchExtendedByServiceName(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(successful(extendedApiDefinition))

      private val result = await(underTest.fetchExtended(serviceName)(FakeRequest("GET", "")))

      status(result) shouldBe OK
      jsonBodyOf(result).asOpt[ExtendedAPIDefinition] shouldBe extendedApiDefinition
    }

    "fail with a 404 (not found) when no public API exists for the given serviceName" in new Setup {

      when(mockAPIDefinitionService.fetchExtendedByServiceName(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(successful(None))

      private val result = await(underTest.fetchExtended(serviceName)(request))

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchExtendedByServiceName(isEq(serviceName), isEq(None))(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.fetchExtended(serviceName)(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "fetch" should {

    "pass on the alsoIncludePrivateTrials option when it is set" in new Setup {
      val alsoIncludePrivateTrials = true
      val alsoIncludePrivateTrialsQueryParameter = "options=alsoIncludePrivateTrials"
      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(None), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(successful(None))

      private val result = await(underTest.fetch(serviceName)(FakeRequest("GET", s"?$alsoIncludePrivateTrialsQueryParameter")))

      status(result) shouldBe NOT_FOUND
      verify(mockAPIDefinitionService).fetchByServiceName(isEq(serviceName), isEq(None), isEq(alsoIncludePrivateTrials))(any[HeaderCarrier])
    }

    "pass on the alsoIncludePrivateTrials option as false when it is not specified" in new Setup {
      val alsoIncludePrivateTrials = false
      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(None), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(successful(None))

      private val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe NOT_FOUND
      verify(mockAPIDefinitionService).fetchByServiceName(isEq(serviceName), isEq(None), isEq(alsoIncludePrivateTrials))(any[HeaderCarrier])
    }

    "succeed with a 200 (ok) when an API exists for the given serviceName and user" in new Setup {

      private val aTime = DateTime.now(DateTimeZone.forID("Asia/Harbin"))

      val apiDefinition = APIDefinition(serviceName, "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.BETA, Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = None, lastPublishedAt = Some(aTime))

      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(Some(userEmail)), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(successful(Some(apiDefinition)))

      private val result = await(underTest.fetch(serviceName)(FakeRequest("GET", s"?email=$userEmail")))

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

      val apiDefinition = APIDefinition(serviceName, "http://calendar", "Calendar API", "My Calendar API", "calendar",
        versions = Seq(APIVersion("1.0", APIStatus.BETA, Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          Some(true))),
        requiresTrust = None)

      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(None), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(successful(Some(apiDefinition)))

      private val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe OK
    }

    "fail with a 404 (not found) when no public API exists for the given serviceName" in new Setup {

      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(None), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(successful(None))

      private val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 404 (not found) when no API exists for the given serviceName and user" in new Setup {

      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(Some(userEmail)), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(successful(None))

      private val result = await(underTest.fetch(serviceName)(FakeRequest("GET", s"?email=$userEmail")))

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {

      when(mockAPIDefinitionService.fetchByServiceName(isEq(serviceName), isEq(None), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.fetch(serviceName)(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "queryDispatcher" should {

    "fail with a 500 (internal server error) when the fetchAllPublicAPIs throws an exception" in new QueryDispatcherSetup {

      when(mockAPIDefinitionService.fetchAllPublicAPIs(alsoIncludePrivateTrials = false))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.queryDispatcher()(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "return all Private APIs when the type parameter is defined as private" in new QueryDispatcherSetup {

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=private")))

      verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

      verify(mockAPIDefinitionService).fetchAllPrivateAPIs()
    }

    "fail with a 500 (internal server error) when private is defined and the service throws an exception" in new QueryDispatcherSetup {

      when(mockAPIDefinitionService.fetchAllPrivateAPIs())
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=private")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with a 400 (bad request) when an invalid type parameter is defined" in new QueryDispatcherSetup {
      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=monoid")))
      status(result) shouldBe BAD_REQUEST
    }

    "fail with a 400 (bad request) when an unrecognised query parameter is passed" in new QueryDispatcherSetup {
      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?invalid-param=true")))
      status(result) shouldBe BAD_REQUEST
      bodyOf(result) shouldBe "Invalid query parameter or parameters"
    }

    "return 404 Not Found when the context is defined and an API does not exist for the context" in new QueryDispatcherSetup {

      when(mockAPIDefinitionService.fetchByContext(isEq("calendar")))
        .thenReturn(successful(None))

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar")))

      status(result) shouldBe NOT_FOUND
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when the context is defined and the service throws an exception" in new QueryDispatcherSetup {

      when(mockAPIDefinitionService.fetchByContext(isEq("calendar")))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with a 500 (internal server error) when the applicationId is defined and the service throws an exception" in new QueryDispatcherSetup {

      when(mockAPIDefinitionService.fetchAllAPIsForApplication(isEq("APP_ID"), any()))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?applicationId=APP_ID")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "fail with a 500 (internal server error) when the email is defined and the service throws an exception" in new QueryDispatcherSetup {

      when(mockAPIDefinitionService.fetchAllAPIsForCollaborator(isEq(userEmail), any[Boolean])(any[HeaderCarrier]))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?email=$userEmail")))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe None
    }

    "return the API when the context is defined and an API exists for the context" in new QueryDispatcherSetup {
      private val context = "my-context"

      private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?context=$context")))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldEqual Json.toJson(apiDefinitions.head)
      getHeader(result, HeaderNames.CACHE_CONTROL) shouldBe Some("max-age=1234")

      verify(mockAPIDefinitionService).fetchByContext(isEq(context))
    }

    "accept an options parameter where alsoIncludePrivateTrials can be specified" when {

      "alsoIncludePrivateTrials is not specified" should {

        val alsoIncludePrivateTrials = false

        "return all the Public APIs (without private trials)" in new QueryDispatcherSetup {

          private val result = await(underTest.queryDispatcher()(request))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllPublicAPIs(alsoIncludePrivateTrials)
        }

        "return all Public APIs (without private trials) when the type parameter is defined as public" in new QueryDispatcherSetup {

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=public")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllPublicAPIs(alsoIncludePrivateTrials)
        }

        "return all the APIs (without private trials) available for an applicationId" in new QueryDispatcherSetup {

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?applicationId=APP_ID")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllAPIsForApplication("APP_ID", alsoIncludePrivateTrials)
        }

        "return all the APIs (without private trials) available for the collaborator when the email is defined" in new QueryDispatcherSetup {

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?email=$userEmail")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllAPIsForCollaborator(isEq(userEmail), isEq(alsoIncludePrivateTrials))(any[HeaderCarrier])
        }
      }

      "alsoIncludePrivateTrials is specified" should {

        val alsoIncludePrivateTrialsQueryParameter = "options=alsoIncludePrivateTrials"
        val alsoIncludePrivateTrials = true

        "return all the Public APIs and private trial APIs" in new QueryDispatcherSetup {

          when(mockAPIDefinitionService.fetchAllPublicAPIs(any())).thenReturn(successful(apiDefinitions))

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?$alsoIncludePrivateTrialsQueryParameter")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllPublicAPIs(alsoIncludePrivateTrials)
        }

        "return all Public APIs and private trial APIs when the type parameter is defined as public" in new QueryDispatcherSetup {

          when(mockAPIDefinitionService.fetchAllPublicAPIs(any())).thenReturn(successful(apiDefinitions))

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?type=public&$alsoIncludePrivateTrialsQueryParameter")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllPublicAPIs(alsoIncludePrivateTrials)
        }

        "be tolerant of query parameters being passed in any order" in new QueryDispatcherSetup {
          when(mockAPIDefinitionService.fetchAllPublicAPIs(any())).thenReturn(successful(apiDefinitions))

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?$alsoIncludePrivateTrialsQueryParameter&type=public")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllPublicAPIs(alsoIncludePrivateTrials)
        }

        "return all the APIs available for an applicationId (including private trials)" in new QueryDispatcherSetup {

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?applicationId=APP_ID&options=alsoIncludePrivateTrials")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllAPIsForApplication("APP_ID", alsoIncludePrivateTrials)
        }

        "return all the APIs available for the collaborator (including private trials) when the email is defined" in new QueryDispatcherSetup {

          private val result = await(underTest.queryDispatcher()(FakeRequest("GET", s"?email=$userEmail&$alsoIncludePrivateTrialsQueryParameter")))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          verify(mockAPIDefinitionService).fetchAllAPIsForCollaborator(isEq(userEmail), isEq(alsoIncludePrivateTrials))(any[HeaderCarrier])
        }
      }
    }
  }

  "validate" should {
    "succeed with status 202 (Accepted) when the payload is valid" in new ValidatorSetup {

      when(mockAPIDefinitionService.fetchByName(any())).thenReturn(Future.successful(None))
      when(mockAPIDefinitionService.fetchByServiceBaseUrl(any())).thenReturn(Future.successful(None))
      when(mockAPIDefinitionService.fetchByContext(any())).thenReturn(Future.successful(None))
      thereAreNoOverlappingAPIContexts

      private val result = await(underTest.validate()(request.withBody(Json.parse(calendarApiDefinition))))

      status(result) shouldBe ACCEPTED
    }

    "fail with status 422 (UnprocessableEntity) when the payload is invalid" in new ValidatorSetup {

      private val result = await(underTest.validate()(request.withBody(Json.parse(calendarApiDefinitionMissingDescription))))

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

      given(mockAPIDefinitionService.delete(isEq("service-name"))(any[HeaderCarrier]))
        .willReturn(successful(()))

      private val result = await(underTest.delete("service-name")(request))

      status(result) shouldBe NO_CONTENT
    }

    "fail with status 500 when the deletion fails" in new Setup {

      given(mockAPIDefinitionService.delete(isEq("service-name"))(any[HeaderCarrier]))
        .willReturn(failed(new RuntimeException("Something went wrong")))

      private val result = await(underTest.delete("service-name")(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with status 403 when the deletion is unauthorized" in new Setup {

      given(mockAPIDefinitionService.delete(isEq("service-name"))(any[HeaderCarrier]))
        .willReturn(failed(new UnauthorizedException("Unauthorized")))

      private val result = await(underTest.delete("service-name")(request))

      status(result) shouldBe FORBIDDEN
    }
  }

  "publishAllToAws" should {
    "succeed with status 204 when all APIs are republished" in new Setup {
      given(mockAPIDefinitionService.publishAllToAws()(any[HeaderCarrier])).willReturn(successful(()))

      val result: Result = await(underTest.publishAllToAws()(request))

      status(result) shouldBe NO_CONTENT
      bodyOf(result).isEmpty shouldBe true
    }

    "fail with status 500 and return the error message when it fails to publish" in new Setup {
      val message = "Some error"
      given(mockAPIDefinitionService.publishAllToAws()(any[HeaderCarrier])).willReturn(failed(new RuntimeException(message)))

      val result: Result = await(underTest.publishAllToAws()(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (jsonBodyOf(result) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
      (jsonBodyOf(result) \ "message").as[String] shouldBe message
    }
  }

  "parse query options" should {
    "set alsoIncludePrivateTrials to false if options not specified" in {
      val parsed = QueryOptions(None)
      parsed.alsoIncludePrivateTrials shouldBe false
    }

    "set alsoIncludePrivateTrials to true if set in the query options" in {
      val parsed = QueryOptions(Some("alsoIncludePrivateTrials"))
      parsed.alsoIncludePrivateTrials shouldBe true
    }

    "set alsoIncludePrivateTrials to false if blank in the query options" in {
      val parsed = QueryOptions(Some(""))
      parsed.alsoIncludePrivateTrials shouldBe false
    }

    "throw error if invalid option specified" in {
      val exception = intercept[BadRequestException] {
        QueryOptions(Some("SomeOtherValue"))
      }

      exception.getMessage shouldBe "Invalid options specified: SomeOtherValue"
    }
  }
  private val legacyCalendarApiDefinition =
    """{
      |  "serviceName": "calendar",
      |  "name": "Calendar API",
      |  "description": "My Calendar API",
      |  "serviceBaseUrl": "http://calendar",
      |  "context": "individuals/calendar",
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
      |  "context": "individuals/calendar",
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
