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

package uk.gov.hmrc.apidefinition.controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus.ALPHA
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.http.{BadRequestException, UnauthorizedException}

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.mocks.{APIEventRepositoryMockModule, ApiDefinitionServiceMockModule}
import uk.gov.hmrc.apidefinition.models.ApiEvents.{ApiCreated, NewApiVersion}
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.validators._

class APIDefinitionControllerSpec extends AsyncHmrcSpec
    with StubControllerComponentsFactory with TolerantJsonApiDefinition with FixedClock {

  trait Setup extends ApiDefinitionServiceMockModule with APIEventRepositoryMockModule {

    implicit lazy val request: Request[AnyContentAsEmpty.type] = FakeRequest()

    val serviceName = ServiceName("calendar")
    val userEmail   = "user@email.com"

    val mockApiDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockAppContext: AppConfig                            = mock[AppConfig]
    when(mockAppContext.fetchByContextTtlInSeconds).thenReturn("1234")
    when(mockAppContext.skipContextValidationAllowlist).thenReturn(List())

    val apiContextValidator: ApiContextValidator         = new ApiContextValidator(ApiDefinitionServiceMock.aMock, mockApiDefinitionRepository, mockAppContext)
    val queryParameterValidator: QueryParameterValidator = new QueryParameterValidator()
    val apiEndpointValidator: ApiEndpointValidator       = new ApiEndpointValidator(queryParameterValidator)
    val apiVersionValidator: ApiVersionValidator         = new ApiVersionValidator(apiEndpointValidator)
    val apiDefinitionValidator: ApiDefinitionValidator   = new ApiDefinitionValidator(ApiDefinitionServiceMock.aMock, apiContextValidator, apiVersionValidator)

    val underTest = new APIDefinitionController(apiDefinitionValidator, ApiDefinitionServiceMock.aMock, mockAppContext, stubControllerComponents())
  }

  trait QueryDispatcherSetup extends Setup {

    val apiDefinitions: List[ApiDefinition] =
      Array.fill(2)(
        ApiDefinition(
          ServiceName("MyApiDefinitionServiceName1"),
          "MyUrl",
          "MyName",
          "My description",
          ApiContext("MyContext"),
          Map.empty,
          false,
          None,
          List(ApiCategory.AGENTS)
        )
      ).toList

    ApiDefinitionServiceMock.FetchByContext.success(apiDefinitions.head)
    ApiDefinitionServiceMock.FetchAllPublicAPIs.success(apiDefinitions)
    ApiDefinitionServiceMock.FetchAllPrivateAPIs.success(apiDefinitions)
    ApiDefinitionServiceMock.FetchAll.success(apiDefinitions)

    def verifyApiDefinitionsReturnedOkWithNoCacheControl(result: Future[Result]) = {
      status(result) shouldBe OK
      contentAsJson(result) shouldEqual Json.toJson(apiDefinitions)
      header(HeaderNames.CACHE_CONTROL, result) shouldBe None
    }
  }

  trait ValidatorSetup extends Setup {

    ApiDefinitionServiceMock.FetchByContext.returnsNone()
    ApiDefinitionServiceMock.FetchByName.returnsNone()
    ApiDefinitionServiceMock.FetchByServiceUrL.returnsNone()

    when(mockApiDefinitionRepository.fetchByServiceName(*[ServiceName])).thenReturn(successful(None))

    def theServiceWillCreateOrUpdateTheAPIDefinition = {
      ApiDefinitionServiceMock.CreateOrUpdate.success()
    }

    def thereAreNoOverlappingAPIContexts =
      when(mockApiDefinitionRepository.fetchAllByTopLevelContext(*[ApiContext])).thenReturn(successful(Seq.empty))
  }

  "createOrUpdate" should {

    "succeed with a 204 (NO CONTENT) when payload is valid and service responds successfully" in new ValidatorSetup {

      val apiDefinition =
        StoredApiDefinition(
          ServiceName("calendar"),
          "http://calendar",
          "Calendar API",
          "My Calendar API",
          ApiContext("individuals/calendar"),
          versions =
            List(
              ApiVersion(
                ApiVersionNbr("1.0"),
                ApiStatus.STABLE,
                ApiAccess.PUBLIC,
                List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
                true
              )
            ),
          isTestSupport = false,
          lastPublishedAt = None,
          List(ApiCategory.OTHER)
        )

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition)))

      status(result) shouldBe NO_CONTENT
      ApiDefinitionServiceMock.CreateOrUpdate.verifyCall(apiDefinition)

    }

    "fail with a 422 (invalid request) when the json payload is invalid for the request" in new ValidatorSetup {

      val body = """{ "invalid": "json" }"""

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(body)))

      status(result) shouldBe UNPROCESSABLE_ENTITY

      verifyZeroInteractions(ApiDefinitionServiceMock.aMock)
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new ValidatorSetup {

      thereAreNoOverlappingAPIContexts
      ApiDefinitionServiceMock.CreateOrUpdate.thenFails()

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(calendarApiDefinition)))

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
          |   "categories" : ["OTHER"],
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
      verifyZeroInteractions(ApiDefinitionServiceMock.aMock)

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(body)))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      contentAsJson(result).as[ValidationErrors] shouldBe
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
          |   "categories" : ["OTHER"],
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
      verifyZeroInteractions(ApiDefinitionServiceMock.aMock)

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(body)))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      contentAsJson(result).as[ValidationErrors] shouldBe
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
          |  "categories" : ["OTHER"],
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

      val apiDefinition = StoredApiDefinition(
        ServiceName("calendar"),
        "http://calendar",
        "Calendar API",
        "My Calendar API",
        ApiContext("individuals/calendar"),
        versions = List(ApiVersion(
          ApiVersionNbr("1.0"),
          ApiStatus.STABLE,
          ApiAccess.PUBLIC,
          List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          true
        )),
        isTestSupport = false,
        lastPublishedAt = None,
        List(ApiCategory.OTHER)
      )

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      val result = underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson)))

      status(result) shouldBe NO_CONTENT

      ApiDefinitionServiceMock.CreateOrUpdate.verifyCall(apiDefinition)

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
          |  "categories" : ["OTHER"],
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

      val apiDefinition = StoredApiDefinition(
        ServiceName("calendar"),
        "http://calendar",
        "Calendar API",
        "My Calendar API",
        ApiContext("individuals/calendar"),
        versions =
          List(ApiVersion(
            ApiVersionNbr("1.0"),
            ApiStatus.STABLE,
            ApiAccess.PUBLIC,
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
            true
          )),
        false,
        None,
        List(ApiCategory.OTHER)
      )

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      private val result = underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson)))

      status(result) shouldBe NO_CONTENT

      ApiDefinitionServiceMock.CreateOrUpdate.verifyCall(apiDefinition)
    }

    "parse an API definition with PRIVATE access type" in new ValidatorSetup {
      private val apiDefinitionJson =
        s"""{
           |  "serviceName": "calendar",
           |  "name": "Calendar API",
           |  "description": "My Calendar API",
           |  "serviceBaseUrl": "http://calendar",
           |  "context": "individuals/calendar",
           |  "requiresTrust": true,
           |  "categories" : ["OTHER"],
           |  "versions": [
           |  {
           |    "version" : "1.0",
           |    "status" : "STABLE",
           |    "access" : {
           |      "type" : "PRIVATE"
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

      val apiDefinition = StoredApiDefinition(
        ServiceName("calendar"),
        "http://calendar",
        "Calendar API",
        "My Calendar API",
        ApiContext("individuals/calendar"),
        versions = List(ApiVersion(
          ApiVersionNbr("1.0"),
          ApiStatus.STABLE,
          ApiAccess.Private(false),
          List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          true
        )),
        false,
        None,
        List(ApiCategory.OTHER)
      )

      thereAreNoOverlappingAPIContexts
      theServiceWillCreateOrUpdateTheAPIDefinition

      private val result = underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson)))

      status(result) shouldBe NO_CONTENT
      ApiDefinitionServiceMock.CreateOrUpdate.verifyCall(apiDefinition)
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

      verifyZeroInteractions(ApiDefinitionServiceMock.aMock)

      private val result = underTest.createOrUpdate()(request.withBody(Json.parse(apiDefinitionJson)))

      status(result) shouldBe UNPROCESSABLE_ENTITY
      (contentAsJson(result) \ "message").as[String] shouldBe "Json cannot be converted to API Definition"
    }
  }

  "fetch" should {
    "succeed with a 200 (ok) when a public API exists for the given serviceName" in new Setup {
      val apiDefinition = ApiDefinition(
        serviceName,
        "http://calendar",
        "Calendar API",
        "My Calendar API",
        ApiContext("calendar"),
        versions = Map(ApiVersionNbr("1.0") -> ApiVersion(
          ApiVersionNbr("1.0"),
          ApiStatus.BETA,
          ApiAccess.PUBLIC,
          List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
          true
        )),
        false,
        None,
        List(ApiCategory.AGENTS)
      )

      ApiDefinitionServiceMock.FetchByServiceName.success(serviceName, apiDefinition)

      private val result = underTest.fetch(serviceName)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(apiDefinition)
    }

    "fail with a 404 (not found) when no API exists for the given serviceName" in new Setup {
      ApiDefinitionServiceMock.FetchByServiceName.returnsNone()

      private val result = underTest.fetch(serviceName)(request)

      status(result) shouldBe NOT_FOUND
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {
      ApiDefinitionServiceMock.FetchByServiceName.thenFails()

      private val result = underTest.fetch(serviceName)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "queryDispatcher" should {

    "fail with a 500 (internal server error) when the fetchAllPublicAPIs throws an exception" in new QueryDispatcherSetup {
      ApiDefinitionServiceMock.FetchAllPublicAPIs.thenFails(alsoIncludePrivateTrials = false)

      private val result = underTest.queryDispatcher()(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      header(HeaderNames.CACHE_CONTROL, result) shouldBe None
    }

    "return all Private APIs when the type parameter is defined as private" in new QueryDispatcherSetup {

      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=private"))

      verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

      verify(ApiDefinitionServiceMock.aMock).fetchAllPrivateAPIs()
    }

    "fail with a 500 (internal server error) when private is defined and the service throws an exception" in new QueryDispatcherSetup {
      ApiDefinitionServiceMock.FetchAllPrivateAPIs.thenFails()

      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=private"))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return all APIs when the type parameter is defined as all" in new QueryDispatcherSetup {
      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=all"))

      verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

      ApiDefinitionServiceMock.FetchAll.verifyCalled()
    }

    "fail with a 500 (internal server error) when all is defined and the service throws an exception" in new QueryDispatcherSetup {
      ApiDefinitionServiceMock.FetchAll.thenFails()

      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=all"))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with a 400 (bad request) when an invalid type parameter is defined" in new QueryDispatcherSetup {
      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=monoid"))
      status(result) shouldBe BAD_REQUEST
    }

    "fail with a 400 (bad request) when an unrecognised query parameter is passed" in new QueryDispatcherSetup {
      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?invalid-param=true"))
      status(result) shouldBe BAD_REQUEST
      contentAsString(result) shouldBe "Invalid query parameter or parameters"
    }

    "return 404 Not Found when the context is defined and an API does not exist for the context" in new QueryDispatcherSetup {
      ApiDefinitionServiceMock.FetchByContext.returnsNoneForContext(ApiContext("calendar"))

      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar"))

      status(result) shouldBe NOT_FOUND
      header(HeaderNames.CACHE_CONTROL, result) shouldBe None
    }

    "fail with a 500 (internal server error) when the context is defined and the service throws an exception" in new QueryDispatcherSetup {
      ApiDefinitionServiceMock.FetchByContext.thenFails()

      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?context=calendar"))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return the API when the context is defined and an API exists for the context" in new QueryDispatcherSetup {
      private val context = "my-context"

      private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?context=$context"))

      status(result) shouldBe OK
      contentAsJson(result) shouldEqual Json.toJson(apiDefinitions.head)
      header(HeaderNames.CACHE_CONTROL, result) shouldBe Some("max-age=1234")

      ApiDefinitionServiceMock.FetchByContext.verifyCalled(ApiContext(context))

    }

    "accept an options parameter where alsoIncludePrivateTrials can be specified" when {

      "alsoIncludePrivateTrials is not specified" should {

        val alsoIncludePrivateTrials = false

        "return all the Public APIs (without private trials)" in new QueryDispatcherSetup {

          private val result = underTest.queryDispatcher()(request)

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          ApiDefinitionServiceMock.FetchAllPublicAPIs.verifyCalled(alsoIncludePrivateTrials)
        }

        "return all Public APIs (without private trials) when the type parameter is defined as public" in new QueryDispatcherSetup {

          private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=public"))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          ApiDefinitionServiceMock.FetchAllPublicAPIs.verifyCalled(alsoIncludePrivateTrials)
        }
      }

      "alsoIncludePrivateTrials is specified" should {

        val alsoIncludePrivateTrialsQueryParameter = "options=alsoIncludePrivateTrials"
        val alsoIncludePrivateTrials               = true

        "return all the Public APIs and private trial APIs" in new QueryDispatcherSetup {
          ApiDefinitionServiceMock.FetchAllPublicAPIs.success(apiDefinitions)

          private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?$alsoIncludePrivateTrialsQueryParameter"))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          ApiDefinitionServiceMock.FetchAllPublicAPIs.verifyCalled(alsoIncludePrivateTrials)
        }

        "return all Public APIs and private trial APIs when the type parameter is defined as public" in new QueryDispatcherSetup {
          ApiDefinitionServiceMock.FetchAllPublicAPIs.success(apiDefinitions)

          private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?type=public&$alsoIncludePrivateTrialsQueryParameter"))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          ApiDefinitionServiceMock.FetchAllPublicAPIs.verifyCalled(alsoIncludePrivateTrials)
        }

        "be tolerant of query parameters being passed in any order" in new QueryDispatcherSetup {
          ApiDefinitionServiceMock.FetchAllPublicAPIs.success(apiDefinitions)

          private val result = underTest.queryDispatcher()(FakeRequest("GET", s"?$alsoIncludePrivateTrialsQueryParameter&type=public"))

          verifyApiDefinitionsReturnedOkWithNoCacheControl(result)

          ApiDefinitionServiceMock.FetchAllPublicAPIs.verifyCalled(alsoIncludePrivateTrials)
        }
      }
    }
  }

  "validate" should {
    "succeed with status 202 (Accepted) when the payload is valid" in new ValidatorSetup {
      ApiDefinitionServiceMock.FetchByName.returnsNone()
      ApiDefinitionServiceMock.FetchByServiceBaseUrl.returnsNone()
      ApiDefinitionServiceMock.FetchByContext.returnsNone()

      thereAreNoOverlappingAPIContexts

      private val result = underTest.validate()(request.withBody(Json.parse(calendarApiDefinition)))

      status(result) shouldBe ACCEPTED
    }

    "fail with status 422 (UnprocessableEntity) when the payload is invalid" in new ValidatorSetup {

      private val result = underTest.validate()(request.withBody(Json.parse(calendarApiDefinitionMissingDescription)))

      contentAsJson(result) shouldEqual Json.toJson(
        ErrorResponse(
          ErrorCode.API_INVALID_JSON,
          "Json cannot be converted to API Definition",
          Some(List(
            FieldErrorDescription("/description", "element is missing")
          ))
        )
      )
    }
  }

  "delete" should {
    "succeed with status 204 (NoContent) when the deletion succeeds" in new Setup {
      ApiDefinitionServiceMock.Delete.success(ServiceName("service-name"))

      private val result = underTest.delete(ServiceName("service-name"))(request)

      status(result) shouldBe NO_CONTENT
    }

    "fail with status 500 when the deletion fails" in new Setup {
      ApiDefinitionServiceMock.Delete.thenFailsWith(new RuntimeException("Something went wrong"))

      private val result = underTest.delete(ServiceName("service-name"))(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "fail with status 403 when the deletion is unauthorized" in new Setup {

      ApiDefinitionServiceMock.Delete.thenFailsWith(new UnauthorizedException("Unauthorized"))

      private val result = underTest.delete(ServiceName("service-name"))(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "publishAllToAws" should {
    "succeed with status 204 when all APIs are republished" in new Setup {

      ApiDefinitionServiceMock.PublishAllToAws.success()

      val result = underTest.publishAllToAws()(request)

      status(result) shouldBe NO_CONTENT
      contentAsString(result).isEmpty shouldBe true
    }

    "fail with status 500 and return the error message when it fails to publish" in new Setup {
      val message = "Some error"
      ApiDefinitionServiceMock.PublishAllToAws.thenFailsWith(message)

      val result = underTest.publishAllToAws()(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe message
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

  "fetchEvents" should {
    "return a list of events for the given serviceName" in new Setup {
      val version1                = ApiVersionNbr("1.0")
      val apiName                 = "Api 123"
      val apiCreated              = ApiCreated(ApiEventId.random, apiName, serviceName, instant)
      val newApiVersion           = NewApiVersion(ApiEventId.random, apiName, serviceName, instant, ALPHA, version1)
      val apiList: List[ApiEvent] = List(apiCreated, newApiVersion)

      ApiDefinitionServiceMock.FetchEventsByServiceName.success(serviceName, apiList)

      private val result = underTest.fetchEvents(serviceName)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
           |[
           |  {
           |    "id": "${apiCreated.id.value}",
           |    "serviceName": "${apiCreated.serviceName}",
           |    "eventDateTime": "2020-01-02T03:04:05.006Z",
           |    "eventType": "${apiCreated.asMetaData()._1}",
           |    "metaData": []
           |  },
           |  {
           |    "id": "${newApiVersion.id.value}",
           |    "serviceName": "${newApiVersion.serviceName}",
           |    "eventDateTime": "2020-01-02T03:04:05.006Z",
           |    "eventType": "${newApiVersion.asMetaData()._1}",
           |    "metaData": [
           |      "${newApiVersion.asMetaData()._2(0)}",
           |      "${newApiVersion.asMetaData()._2(1)}"
           |    ]
           |  }
           |]
           |""".stripMargin
      )
    }

    "return a list of events for the given serviceName, excluding no change events" in new Setup {
      val version1                = ApiVersionNbr("1.0")
      val apiName                 = "Api 123"
      val apiCreated              = ApiCreated(ApiEventId.random, apiName, serviceName, instant)
      val newApiVersion           = NewApiVersion(ApiEventId.random, apiName, serviceName, instant, ALPHA, version1)
      val apiList: List[ApiEvent] = List(apiCreated, newApiVersion)

      ApiDefinitionServiceMock.FetchEventsByServiceName.success(serviceName, apiList, includeNoChange = false)

      private val result = underTest.fetchEvents(serviceName, includeNoChange = false)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
           |[
           |  {
           |    "id": "${apiCreated.id.value}",
           |    "serviceName": "${apiCreated.serviceName}",
           |    "eventDateTime": "2020-01-02T03:04:05.006Z",
           |    "eventType": "${apiCreated.asMetaData()._1}",
           |    "metaData": []
           |  },
           |  {
           |    "id": "${newApiVersion.id.value}",
           |    "serviceName": "${newApiVersion.serviceName}",
           |    "eventDateTime": "2020-01-02T03:04:05.006Z",
           |    "eventType": "${newApiVersion.asMetaData()._1}",
           |    "metaData": [
           |      "${newApiVersion.asMetaData()._2(0)}",
           |      "${newApiVersion.asMetaData()._2(1)}"
           |    ]
           |  }
           |]
           |""".stripMargin
      )
    }

    "return an empty list if no events found for the given serviceName" in new Setup {
      ApiDefinitionServiceMock.FetchEventsByServiceName.success(serviceName, List.empty)

      private val result = underTest.fetchEvents(serviceName)(request)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse("[]")
    }

    "fail with a 500 (internal server error) when the service throws an exception" in new Setup {
      ApiDefinitionServiceMock.FetchEventsByServiceName.thenFails()

      private val result = underTest.fetchEvents(serviceName)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "deleteEvents" should {
    "delete all events for the given serviceName" in new Setup {
      ApiDefinitionServiceMock.DeleteEventsByServiceName.success(serviceName)

      private val result = underTest.deleteEvents(serviceName)(request)

      status(result) shouldBe OK
    }
  }

  private val calendarApiDefinition =
    """{
      |  "serviceName": "calendar",
      |  "name": "Calendar API",
      |  "description": "My Calendar API",
      |  "serviceBaseUrl": "http://calendar",
      |  "context": "individuals/calendar",
      |  "requiresTrust": true,
      |  "categories" : ["OTHER"],
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
