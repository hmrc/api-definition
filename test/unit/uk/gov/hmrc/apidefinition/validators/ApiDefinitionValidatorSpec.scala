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

package unit.uk.gov.hmrc.apidefinition.validators

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Results.{NoContent, UnprocessableEntity}
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.APICategory.OTHER
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.validators._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class ApiDefinitionValidatorSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    val mockAPIDefinitionService: APIDefinitionService = mock[APIDefinitionService]
    val mockApiDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockAppConfig: AppConfig = mock[AppConfig]
    when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List())
    val apiContextValidator: ApiContextValidator = new ApiContextValidator(mockAPIDefinitionService, mockApiDefinitionRepository, mockAppConfig)
    val queryParameterValidator: QueryParameterValidator = new QueryParameterValidator()
    val apiEndpointValidator: ApiEndpointValidator = new ApiEndpointValidator(queryParameterValidator)
    val apiVersionValidator: ApiVersionValidator = new ApiVersionValidator(apiEndpointValidator)
    val apiDefinitionValidator: ApiDefinitionValidator = new ApiDefinitionValidator(mockAPIDefinitionService, apiContextValidator, apiVersionValidator)

    when(mockAPIDefinitionService.fetchByContext(any[String])).thenReturn(successful(None))
    when(mockAPIDefinitionService.fetchByName(any[String])).thenReturn(successful(None))
    when(mockAPIDefinitionService.fetchByServiceBaseUrl(any[String])).thenReturn(successful(None))
    when(mockApiDefinitionRepository.fetchByServiceName(any[String])).thenReturn(successful(None))
    when(mockApiDefinitionRepository.fetchAllByTopLevelContext(any[String])).thenReturn(Future.successful(Seq.empty))
    
    def assertValidationSuccess(apiDefinition: => APIDefinition): Unit = {
      val result = await(apiDefinitionValidator.validate(apiDefinition)(_ => Future.successful(NoContent)))
      result.header.status shouldBe NoContent.header.status
      result.body.isKnownEmpty shouldBe true
    }

    def assertValidationFailure(apiDefinition: => APIDefinition, failureMessages: Seq[String]): Unit = {
      implicit val sys: ActorSystem = ActorSystem("ApiDefinitionValidatorTest")
      implicit val mat: ActorMaterializer = ActorMaterializer()

      val result = await(apiDefinitionValidator.validate(apiDefinition)(_ => Future.successful(NoContent)))
      result.header.status shouldBe UnprocessableEntity.header.status

      val validationErrors = jsonBodyOf(result).as[ValidationErrors]
      validationErrors.code shouldBe INVALID_REQUEST_PAYLOAD
      validationErrors.messages shouldBe failureMessages
    }

    val calendarApi = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "individuals/calendar",
      Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()),
        Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))),
      Some(false), categories = Some(Seq(OTHER)))
  }

  "ApiDefinitionValidator" should {

    "fail validation if an empty serviceBaseUrl is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(serviceBaseUrl = "")

      assertValidationFailure(apiDefinition, List("Field 'serviceBaseUrl' should not be empty for API 'Calendar API'"))
      verify(mockAPIDefinitionService, never()).fetchByServiceBaseUrl(any[String])
    }

    "fail validation if an empty serviceName is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(serviceName = "")

      assertValidationFailure(apiDefinition, List("Field 'serviceName' should not be empty for API 'Calendar API'"))
    }

    "fail validation if a version number is referenced more than once" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions =
        Seq(
          APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("1.1", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("1.1", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("1.2", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))))
      )

      assertValidationFailure(apiDefinition, List("Field 'version' must be unique for API 'Calendar API'"))
    }

    "fail validation if an empty name is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(name = "")

      assertValidationFailure(apiDefinition, List("Field 'name' should not be empty for API with service name 'calendar'"))
      verify(mockAPIDefinitionService, never()).fetchByName(any[String])
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
      lazy val apiDefinition: APIDefinition = calendarApi.copy(categories = Some(Seq()))

      assertValidationFailure(apiDefinition, List("Field 'categories' should exist and not be empty for API 'Calendar API'"))
    }

    "fail validation when no APIVersion is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions = Nil)
      assertValidationFailure(apiDefinition, List("Field 'versions' must not be empty for API 'Calendar API'"))
    }

    "fail validation when if there is an API version without version number" in new Setup {
      lazy val versions: Seq[APIVersion] =
        Seq(
          APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))
        )
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions = versions)

      assertValidationFailure(apiDefinition, List("Field 'versions.version' is required for API 'Calendar API'"))
    }

    "fail validation when no Endpoint is provided" in new Setup {
      lazy val apiDefinition: APIDefinition = calendarApi.copy(versions = Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Nil)))

      assertValidationFailure(apiDefinition, List("Field 'versions.endpoints' must not be empty for API 'Calendar API' version '1.0'"))
    }

    val moneyEndpoint = Endpoint(
      uriPattern = "/payments",
      endpointName = "Check Payments",
      method = HttpMethod.GET,
      authType = AuthType.USER,
      throttlingTier = ResourceThrottlingTier.UNLIMITED,
      scope = Some("read:money"))

    val moneyApiVersion = APIVersion(
      version = "1.0",
      status = APIStatus.PROTOTYPED,
      endpoints = Seq(moneyEndpoint))

    lazy val moneyApiDefinition = APIDefinition(
      serviceName = "money",
      serviceBaseUrl = "http://www.money.com",
      name = "Money API",
      description = "API for checking payments",
      context = "individuals/money",
      versions = Seq(moneyApiVersion),
      requiresTrust = Some(false),
      categories = Some(Seq(OTHER)))

    val specialChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(' ,')'
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
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = ""))))
      )

      assertValidationFailure(apiDefinition, List("Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    specialChars.foreach { char: Char =>
      s"fail validation if the endpoint contains $char in the URI" in new Setup {
        lazy val endpointUri = s"/payments$char"
        lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
          versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpointUri))))
        )

        assertValidationFailure(apiDefinition, List(s"Field 'endpoints.uriPattern' with value '$endpointUri' should" +
          " match regular expression '^/[a-zA-Z0-9_\\-\\/{}]*$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
      }
    }

    "pass validation if the API definition contains the root endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = "/"))))
      )
      assertValidationSuccess(apiDefinition)
    }

    "fail validation if the endpoint has no name" in new Setup {
      val endpoint =  "/hello/friend"
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpoint, endpointName = ""))))
      )

      assertValidationFailure(apiDefinition, List(s"Field 'endpoints.endpointName' is required for API 'Money API' version '1.0'"))
    }

    "fail validation if the endpoint defines path parameters with ':'" in new Setup {
      val endpoint =  "/hello/:friend"
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpoint))))
      )

      assertValidationFailure(apiDefinition, List(s"Field 'endpoints.uriPattern' with value '$endpoint' should match regular expression '^/[a-zA-Z0-9_\\-\\/{}]*$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    val pathParameterUris = Map("/{}" -> "{}", "/}{" -> "}{", "/hello{{friend}}" -> "hello{{friend}}",
      "/hello/my{brother}" -> "my{brother}", "/hello/}friend{" -> "}friend{", "/hello/{0friend}" -> "{0friend}")
    pathParameterUris.foreach { case (endpointUri: String, segment: String) =>
      s"fail validation if the endpoint ($endpointUri) defines path parameters incorrectly" in new Setup {
        lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
          versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpointUri))))
        )

        assertValidationFailure(apiDefinition, List(s"Curly-bracketed segment '$segment' should match regular " +
          "expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
      }
    }

    s"fail validation if the endpoint defines multiple path parameters incorrectly" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = "/hello/{my/friend}"))))
      )

      assertValidationFailure(apiDefinition, List(
        s"Curly-bracketed segment '{my' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'",
        s"Curly-bracketed segment 'friend}' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    val moneyQueryParameter = Parameter("startDate")
    "fail validation when a query parameter name is empty" in new Setup {

      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(queryParameters = Some(Seq(moneyQueryParameter.copy(name = "")))))))
      )

      assertValidationFailure(apiDefinition, List("Field 'queryParameters.name' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    ('/' :: '{' :: '}' :: specialChars).foreach { char =>
      s"fail validation when a query parameter name contains '$char' in the name" in new Setup {

        val queryParamName = s"param$char"
        lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
          versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(queryParameters = Some(Seq(moneyQueryParameter.copy(name = queryParamName)))))))
        )

        assertValidationFailure(apiDefinition, List(s"Field 'queryParameters.name' with value '$queryParamName' should" +
          " match regular expression '^[a-zA-Z0-9_\\-]+$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
      }
    }

    "fail validation when no scopes are provided for a user restricted endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(scope = None)))))
      assertValidationFailure(apiDefinition, List("Field 'endpoints.scope' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    "pass validation when no scopes are provided for an application restricted endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(authType = AuthType.APPLICATION, scope = None)))))
      assertValidationSuccess(apiDefinition)
    }

    "pass validation when scopes are provided for an application restricted endpoint" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(authType = AuthType.APPLICATION, scope = Some("scope"))))))
      assertValidationSuccess(apiDefinition)
    }

    "accumulate multiple errors in the response" in new Setup {
      lazy val apiDefinition: APIDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = ""))))
      )
      when(mockAPIDefinitionService.fetchByContext("individuals/money"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))
      when(mockAPIDefinitionService.fetchByName("Money API"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))
      when(mockAPIDefinitionService.fetchByServiceBaseUrl("http://www.money.com"))
        .thenReturn(successful(Some(moneyApiDefinition.copy(serviceName = "anotherService"))))

      assertValidationFailure(apiDefinition, List(
        "Field 'context' must be unique for API 'Money API'",
        "Field 'name' must be unique for API 'Money API'",
        "Field 'serviceBaseUrl' must be unique for API 'Money API'",
        "Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

  }

}
