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

package uk.gov.hmrc.apidefinition.validators

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.mvc.Results.{NoContent, UnprocessableEntity}
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ApiDefinitionValidatorSpec extends UnitSpec {

  "ApiDefinitionValidator" should {

    "fail validation if an empty serviceBaseUrl is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "", "Calendar API", "My Calendar API", "calendar",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date",
          HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(false))
      assertValidationFailure(apiDefinition, List("Field 'serviceBaseUrl' should not be empty for API 'Calendar API'"))
    }

    "fail validation if an empty serviceName is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date",
          HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(false))
      assertValidationFailure(apiDefinition, List("Field 'serviceName' should not be empty for API 'Calendar API'"))
    }

    "fail validation if a version number is referenced more than once" in {
      lazy val apiDefinition: APIDefinition = APIDefinition(
        "calendar",
        "http://calendar",
        "Calendar API",
        "My Calendar API",
        "calendar",
        Seq(
          APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("1.1", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("1.1", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))),
          APIVersion("1.2", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))),
        Some(false))
      assertValidationFailure(apiDefinition, List("Field 'version' must be unique for API 'Calendar API'"))
    }

    "fail validation if an empty name is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "http://calendar", "", "My Calendar API", "calendar",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date",
          HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(false))
      assertValidationFailure(apiDefinition, List("Field 'name' should not be empty for API with service name 'calendar'"))
    }

    "fail validation if an empty context is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date",
          HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(false))
      assertValidationFailure(apiDefinition, List("Field 'context' should not be empty for API 'Calendar API'"))
    }

    "fail validation if an empty description is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "", "calendar",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date",
          HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(false))
      assertValidationFailure(apiDefinition, List("Field 'description' should not be empty for API 'Calendar API'"))
    }

    "fail validation when no APIVersion is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar", Nil, None)
      assertValidationFailure(apiDefinition, List("Field 'versions' must not be empty for API 'Calendar API'"))
    }

    "fail validation when no Endpoint is provided" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Nil)), Some(false))
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
      context = "money",
      versions = Seq(moneyApiVersion),
      requiresTrust = Some(false))

    "fail validation when the context starts with '/' " in {
      lazy val apiDefinition = moneyApiDefinition.copy(context = "/hi")
      assertValidationFailure(apiDefinition, List("Field 'context' should not start with '/' for API 'Money API'"))
    }

    "fail validation when the context ends with '/' " in {
      lazy val apiDefinition = moneyApiDefinition.copy(context = "hi/")
      assertValidationFailure(apiDefinition, List("Field 'context' should not end with '/' for API 'Money API'"))
    }

    "fail validation when the context contains '//' " in {
      lazy val apiDefinition = moneyApiDefinition.copy(context = "hi//aloha")
      assertValidationFailure(apiDefinition, List("Field 'context' should not have empty path segments for API 'Money API'"))
    }

    val specialChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', ''',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(' ,')'
    )

    ('{' :: '}' :: specialChars).foreach { char: Char =>
      s"fail validation if the API contains '$char' in the context" in {
        lazy val ctx = s"my-context_$char"
        lazy val apiDefinition = moneyApiDefinition.copy(context = ctx)
        assertValidationFailure(apiDefinition, List("Field 'context' should match regular expression '^[a-zA-Z0-9_\\-\\/]+$' for API 'Money API'"))
      }
    }

    "fail validation when the endpoint URI is empty" in {

      lazy val apiDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = ""))))
      )

      assertValidationFailure(apiDefinition, List("Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    specialChars.foreach { char: Char =>
      s"fail validation if the endpoint contains $char in the URI" in {
        lazy val endpointUri = s"/payments$char"
        lazy val apiDefinition = moneyApiDefinition.copy(
          versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpointUri))))
        )

        assertValidationFailure(apiDefinition, List(s"Field 'endpoints.uriPattern' with value '$endpointUri' should match regular expression '^/[a-zA-Z0-9_\\-\\/{}]*$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
      }
    }

    "pass validation if the API definition contains the root endpoint" in {
      lazy val apiDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = "/"))))
      )
      assertValidationSuccess(apiDefinition)
    }

    "fail validation if the endpoint has no name" in {

      val endpoint =  "/hello/friend"
      lazy val apiDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpoint, endpointName = ""))))
      )

      assertValidationFailure(apiDefinition, List(s"Field 'endpoints.endpointName' is required for API 'Money API' version '1.0'"))
    }

    "fail validation if the endpoint defines path parameters with ':'" in {

      val endpoint =  "/hello/:friend"
      lazy val apiDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpoint))))
      )

      assertValidationFailure(apiDefinition, List(s"Field 'endpoints.uriPattern' with value '$endpoint' should match regular expression '^/[a-zA-Z0-9_\\-\\/{}]*$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    val pathParameterUris = Map("/{}" -> "{}", "/}{" -> "}{", "/hello{{friend}}" -> "hello{{friend}}",
      "/hello/my{brother}" -> "my{brother}", "/hello/}friend{" -> "}friend{", "/hello/{0friend}" -> "{0friend}")
    pathParameterUris.foreach { case (endpointUri: String, segment: String) =>
      s"fail validation if the endpoint ($endpointUri) defines path parameters incorrectly" in {
        lazy val apiDefinition = moneyApiDefinition.copy(
          versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = endpointUri))))
        )

        assertValidationFailure(apiDefinition, List(s"Curly-bracketed segment '$segment' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
      }
    }

    s"fail validation if the endpoint defines multiple path parameters incorrectly" in {
      lazy val apiDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(uriPattern = "/hello/{my/friend}"))))
      )

      assertValidationFailure(apiDefinition, List(
        s"Curly-bracketed segment '{my' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'",
        s"Curly-bracketed segment 'friend}' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    val moneyQueryParameter = Parameter("startDate")
    "fail validation when a query parameter name is empty" in {

      lazy val apiDefinition = moneyApiDefinition.copy(
        versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(queryParameters = Some(Seq(moneyQueryParameter.copy(name = "")))))))
      )

      assertValidationFailure(apiDefinition, List("Field 'queryParameters.name' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
    }

    ('/' :: '{' :: '}' :: specialChars).foreach { char =>
      s"fail validation when a query parameter name contains '$char' in the name" in {

        val queryParamName = s"param$char"
        lazy val apiDefinition = moneyApiDefinition.copy(
          versions = Seq(moneyApiVersion.copy(endpoints = Seq(moneyEndpoint.copy(queryParameters = Some(Seq(moneyQueryParameter.copy(name = queryParamName)))))))
        )

        assertValidationFailure(apiDefinition, List(s"Field 'queryParameters.name' with value '$queryParamName' should match regular expression '^[a-zA-Z0-9_\\-]+$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"))
      }
    }

    "fail validation when a scope is provided but auth type is 'application'" in {
      lazy val apiDefinition: APIDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar",
        Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/uriPattern", "endpointName", HttpMethod.GET,
          AuthType.APPLICATION, ResourceThrottlingTier.UNLIMITED, Some("scope"))))), None)
      assertValidationFailure(apiDefinition, List("Field 'endpoints.scope' is not allowed for API 'Calendar API' version '1.0' endpoint 'endpointName'"))
    }

  }

  private def assertValidationSuccess(apiDefinition: => APIDefinition): Unit = {
    val result = await(ApiDefinitionValidator.validate(apiDefinition)(_ => Future.successful(NoContent)))
    result.header.status shouldBe NoContent.header.status
    result.body.isKnownEmpty shouldBe true
  }

  private def assertValidationFailure(apiDefinition: => APIDefinition, failureMessages: Seq[String]): Unit = {
    implicit val sys: ActorSystem = ActorSystem("ApiDefinitionValidatorTest")
    implicit val mat: ActorMaterializer = ActorMaterializer()

    val result = await(ApiDefinitionValidator.validate(apiDefinition)(_ => Future.successful(NoContent)))
    result.header.status shouldBe UnprocessableEntity.header.status

    val validationErrors = jsonBodyOf(result).as[ValidationErrors]
    validationErrors.code shouldBe INVALID_REQUEST_PAYLOAD
    validationErrors.messages shouldBe failureMessages
  }

}
