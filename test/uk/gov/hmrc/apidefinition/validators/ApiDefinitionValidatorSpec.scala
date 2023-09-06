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

package uk.gov.hmrc.apidefinition.validators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.mvc.Results.{NoContent, UnprocessableEntity}
import play.api.test.Helpers._

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.QueryParameter

class ApiDefinitionValidatorSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  implicit val mat = app.materializer

  trait Setup {
    val mockAPIDefinitionService: APIDefinitionService       = mock[APIDefinitionService]
    val mockApiDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockAppConfig: AppConfig                             = mock[AppConfig]
    when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List())
    val apiContextValidator: ApiContextValidator             = new ApiContextValidator(mockAPIDefinitionService, mockApiDefinitionRepository, mockAppConfig)
    val queryParameterValidator: QueryParameterValidator     = new QueryParameterValidator()
    val apiEndpointValidator: ApiEndpointValidator           = new ApiEndpointValidator(queryParameterValidator)
    val apiVersionValidator: ApiVersionValidator             = new ApiVersionValidator(apiEndpointValidator)
    val apiDefinitionValidator: ApiDefinitionValidator       = new ApiDefinitionValidator(mockAPIDefinitionService, apiContextValidator, apiVersionValidator)

    when(mockAPIDefinitionService.fetchByContext(*[ApiContext])).thenReturn(successful(None))
    when(mockAPIDefinitionService.fetchByName(*[String])).thenReturn(successful(None))
    when(mockAPIDefinitionService.fetchByServiceBaseUrl(*[String])).thenReturn(successful(None))
    when(mockApiDefinitionRepository.fetchByServiceName(*[String])).thenReturn(successful(None))
    when(mockApiDefinitionRepository.fetchAllByTopLevelContext(*[ApiContext])).thenReturn(successful(Seq.empty))

    def assertValidationSuccess(apiDefinition: => APIDefinition): Unit = {
      val result = await(apiDefinitionValidator.validate(apiDefinition)(_ => successful(NoContent)))
      result.header.status shouldBe NoContent.header.status
      result.body.isKnownEmpty shouldBe true
    }

    def assertValidationFailure(apiDefinition: => APIDefinition, failureMessages: Seq[String]): Unit = {

      val result = apiDefinitionValidator.validate(apiDefinition)(_ => successful(NoContent))
      status(result) shouldBe UnprocessableEntity.header.status

      val validationErrors = contentAsJson(result).as[ValidationErrors]
      validationErrors.code shouldBe INVALID_REQUEST_PAYLOAD
      validationErrors.messages shouldBe failureMessages
    }

    val calendarApi = APIDefinition(
      "calendar",
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      ApiContext("individuals/calendar"),
      List(APIVersion(
        ApiVersionNbr("1.0"),
        ApiStatus.PROTOTYPED,
        Some(ApiAccess.PUBLIC),
        List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
      )),
      Some(false),
      categories = Some(List(ApiCategory.OTHER))
    )
  }

  "ApiDefinitionValidator" should {

    "fail validation if an empty serviceBaseUrl is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(serviceBaseUrl = "")

      assertValidationFailure(apiDefinition, List("Field 'serviceBaseUrl' should not be empty for API 'Calendar API'"))
      verify(mockAPIDefinitionService, never).fetchByServiceBaseUrl(*[String])
    }

    "fail validation if an empty serviceName is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(serviceName = "")

      assertValidationFailure(apiDefinition, List("Field 'serviceName' should not be empty for API 'Calendar API'"))
    }

    "fail validation if a version number is referenced more than once" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions =
        List(
          APIVersion(
            ApiVersionNbr("1.0"),
            ApiStatus.PROTOTYPED,
            Some(ApiAccess.PUBLIC),
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
          ),
          APIVersion(
            ApiVersionNbr("1.1"),
            ApiStatus.PROTOTYPED,
            Some(ApiAccess.PUBLIC),
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
          ),
          APIVersion(
            ApiVersionNbr("1.1"),
            ApiStatus.PROTOTYPED,
            Some(ApiAccess.PUBLIC),
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
          ),
          APIVersion(
            ApiVersionNbr("1.2"),
            ApiStatus.PROTOTYPED,
            Some(ApiAccess.PUBLIC),
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
          )
        )
      )

      assertValidationFailure(apiDefinition, List("Field 'version' must be unique for API 'Calendar API'"))
    }

    "fail validation if an empty name is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(name = "")

      assertValidationFailure(apiDefinition, List("Field 'name' should not be empty for API with service name 'calendar'"))
      verify(mockAPIDefinitionService, never).fetchByName(*[String])
    }

    "fail validation if an empty description is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(description = "")

      assertValidationFailure(apiDefinition, List("Field 'description' should not be empty for API 'Calendar API'"))
    }

    "fail validation if categories is None" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(categories = None)

      assertValidationFailure(apiDefinition, List("Field 'categories' should exist and not be empty for API 'Calendar API'"))
    }

    "fail validation if categories is empty" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(categories = Some(List()))

      assertValidationFailure(apiDefinition, List("Field 'categories' should exist and not be empty for API 'Calendar API'"))
    }

    "fail validation when no APIVersion is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions = Nil)
      assertValidationFailure(apiDefinition, List("Field 'versions' must not be empty for API 'Calendar API'"))
    }

    "fail validation when if there is an API version without version number" in new Setup {
      lazy val versions: List[APIVersion]   =
        List(
          APIVersion(
            ApiVersionNbr("1.0"),
            ApiStatus.PROTOTYPED,
            Some(ApiAccess.PUBLIC),
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
          ),
          APIVersion(
            ApiVersionNbr(""),
            ApiStatus.PROTOTYPED,
            Some(ApiAccess.PUBLIC),
            List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
          )
        )
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions = versions)

      assertValidationFailure(apiDefinition, List("Field 'versions.version' is required for API 'Calendar API'"))
    }

    "fail validation when no Endpoint is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions = List(APIVersion(ApiVersionNbr("1.0"), ApiStatus.PROTOTYPED, Some(ApiAccess.PUBLIC), Nil)))

      assertValidationFailure(apiDefinition, List("Field 'versions.endpoints' must not be empty for API 'Calendar API' version '1.0'"))
    }

    val moneyEndpoint = Endpoint(
      uriPattern = "/payments",
      endpointName = "Check Payments",
      method = HttpMethod.GET,
      authType = AuthType.USER,
      throttlingTier = ResourceThrottlingTier.UNLIMITED,
      scope = Some("read:money")
    )

    val moneyApiVersion = APIVersion(
      version = ApiVersionNbr("1.0"),
      status = ApiStatus.PROTOTYPED,
      endpoints = List(moneyEndpoint)
    )

    lazy val moneyApiDefinition = APIDefinition(
      serviceName = "money",
      serviceBaseUrl = "http://www.money.com",
      name = "Money API",
      description = "API for checking payments",
      context = ApiContext("individuals/money"),
      versions = List(moneyApiVersion),
      requiresTrust = Some(false),
      categories = Some(List(ApiCategory.OTHER))
    )

    val specialChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
    )

    "fail validation when name already exist for another API" in new Setup {
      when(mockAPIDefinitionService.fetchByName("Money API"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))

      assertValidationFailure(moneyApiDefinition, List("Field 'name' must be unique for API 'Money API'"))
    }

    "fail validation when serviceBaseUrl already exists for another API" in new Setup {
      when(mockAPIDefinitionService.fetchByServiceBaseUrl("http://www.money.com"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))

      assertValidationFailure(moneyApiDefinition, List("Field 'serviceBaseUrl' must be unique for API 'Money API'"))
    }

    "fail validation when the endpoint URI is empty" in new Setup {

      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = ""))))
      )

      assertValidationFailure(apiDefinition, List("Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    specialChars.foreach { char: Char =>
      s"fail validation if the endpoint contains $char in the URI" in new Setup {
        lazy val endpointUri                  = s"/payments$char"
        lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
          versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpointUri))))
        )

        assertValidationFailure(
          apiDefinition,
          List(s"Field 'endpoints.uriPattern' with value '$endpointUri' should" +
            " match regular expression '^/[a-zA-Z0-9_\\-\\/{}]*$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
        )
      }
    }

    "pass validation if the API definition contains the root endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = "/"))))
      )
      assertValidationSuccess(apiDefinition)
    }

    "fail validation if the endpoint has no name" in new Setup {
      val endpoint                          = "/hello/friend"
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpoint, endpointName = ""))))
      )

      assertValidationFailure(apiDefinition, List(s"Field 'endpoints.endpointName' is required for API 'Money API' version '1.0'"))
    }

    "fail validation if the endpoint defines path parameters with ':'" in new Setup {
      val endpoint                          = "/hello/:friend"
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpoint))))
      )

      assertValidationFailure(
        apiDefinition,
        List(s"Field 'endpoints.uriPattern' with value '$endpoint' should match regular expression '^/[a-zA-Z0-9_\\-\\/{}]*$$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
      )
    }

    val pathParameterUris = Map(
      "/{}"                -> "{}",
      "/}{"                -> "}{",
      "/hello{{friend}}"   -> "hello{{friend}}",
      "/hello/my{brother}" -> "my{brother}",
      "/hello/}friend{"    -> "}friend{",
      "/hello/{0friend}"   -> "{0friend}"
    )
    pathParameterUris.foreach { case (endpointUri: String, segment: String) =>
      s"fail validation if the endpoint ($endpointUri) defines path parameters incorrectly" in new Setup {
        lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
          versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpointUri))))
        )

        assertValidationFailure(
          apiDefinition,
          List(s"Curly-bracketed segment '$segment' should match regular " +
            "expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
        )
      }
    }

    s"fail validation if the endpoint defines multiple path parameters incorrectly" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = "/hello/{my/friend}"))))
      )

      assertValidationFailure(
        apiDefinition,
        List(
          s"Curly-bracketed segment '{my' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'",
          s"Curly-bracketed segment 'friend}' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"
        )
      )
    }

    val moneyQueryParameter = QueryParameter("startDate")
    "fail validation when a query parameter name is empty" in new Setup {

      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(queryParameters = Some(List(moneyQueryParameter.copy(name = "")))))))
      )

      assertValidationFailure(apiDefinition, List("Field 'queryParameters.name' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    ('/' :: '{' :: '}' :: specialChars).foreach { char =>
      s"fail validation when a query parameter name contains '$char' in the name" in new Setup {

        val queryParamName                    = s"param$char"
        lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
          versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(queryParameters = Some(List(moneyQueryParameter.copy(name = queryParamName)))))))
        )

        assertValidationFailure(
          apiDefinition,
          List(s"Field 'queryParameters.name' with value '$queryParamName' should" +
            " match regular expression '^[a-zA-Z0-9_\\-]+$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
        )
      }
    }

    "fail validation when no scopes are provided for a user restricted endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(scope = None))))
      )
      assertValidationFailure(apiDefinition, List("Field 'endpoints.scope' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    "pass validation when no scopes are provided for an application restricted endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(authType = AuthType.APPLICATION, scope = None))))
      )
      assertValidationSuccess(apiDefinition)
    }

    "pass validation when scopes are provided for an application restricted endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(authType = AuthType.APPLICATION, scope = Some("scope")))))
      )
      assertValidationSuccess(apiDefinition)
    }

    "accumulate multiple errors in the response" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = ""))))
      )
      when(mockAPIDefinitionService.fetchByContext(ApiContext("individuals/money")))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))
      when(mockAPIDefinitionService.fetchByName("Money API"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))
      when(mockAPIDefinitionService.fetchByServiceBaseUrl("http://www.money.com"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))

      assertValidationFailure(
        apiDefinition,
        List(
          "Field 'context' must be unique for API 'Money API'",
          "Field 'name' must be unique for API 'Money API'",
          "Field 'serviceBaseUrl' must be unique for API 'Money API'",
          "Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"
        )
      )
    }

  }

}
