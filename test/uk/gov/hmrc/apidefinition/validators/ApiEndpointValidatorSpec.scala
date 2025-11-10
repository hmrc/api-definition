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

import cats.data.Validated
import org.scalatest.prop.TableDrivenPropertyChecks

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{AuthType, Endpoint, HttpMethod, QueryParameter, ResourceThrottlingTier}

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.validators.ApiEndpointValidator

class ApiEndpointValidatorSpec extends AsyncHmrcSpec with TableDrivenPropertyChecks {

  trait Setup {

    val specialChars                                     = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
    )
    val queryParameterValidator: QueryParameterValidator = new QueryParameterValidator()
    val validator                                        = new ApiEndpointValidator(queryParameterValidator)

    val endpoint: Endpoint = Endpoint("/", "Test Endpoint", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)
  }

  "ApiEndpointValidator" should {
    "allow dots at start of endpoints" in new Setup {

      val x = validator.validate("Error Message")(endpoint.copy("/.well-known/openid-configuration"))

      x match {
        case Validated.Valid(_)        => succeed
        case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
      }
    }

    "not allow dots in middle of endpoints" in new Setup {
      validator.validate("Error Message")(endpoint.copy(uriPattern = "/well.known")) match {
        case Validated.Valid(_)        => fail()
        case Validated.Invalid(errors) => succeed
      }
    }

    "allow endpoints without dots" in new Setup {

      validator.validate("Error Message")(endpoint.copy(uriPattern = "/well-known/openid-configuration")) match {
        case Validated.Valid(_)        => succeed
        case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
      }
    }

    "allow valid endpoints" in new Setup {

      validator.validate("Error Message")(endpoint.copy(uriPattern = "/paye/{nino}/eligibility-check-digitally-excluded")) match {
        case Validated.Valid(_)        => succeed
        case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
      }
    }

    "fail validation if the endpoint contains in the URI" in new Setup {
      specialChars.foreach { char: Char =>
        val endpointUri = s"/payments$char"
        validator.validate("Error Message")(endpoint.copy(uriPattern = endpointUri)) match {
          case Validated.Valid(_)        => fail(s"$char should fail validation")
          case Validated.Invalid(errors) => {
            println(errors.toList.mkString)
            succeed
          }
        }
      }
    }

    "detect duplicate parameter names" in new Setup {
      val errorContext = "for API 'My API' version '1.0'"
      val values       = Table(
        ("URIPattern", "Query params", "Error message"),
        (
          "/{alpha}",
          List("alpha"),
          s"Duplicate name for path and query parameters: {alpha} $errorContext endpoint '${endpoint.endpointName}'"
        ),
        (
          "/{alpha}/with/{beta}",
          List("alpha", "beta"),
          s"Duplicate name for path and query parameters: {alpha},{beta} $errorContext endpoint '${endpoint.endpointName}'"
        )
      )

      forAll(values) { case (uriPattern, queryParams, errorMessage) =>
        val testEndpoint: Endpoint = endpoint.copy(
          uriPattern,
          queryParameters = queryParams.map(QueryParameter(_, required = true))
        )

        validator.validate(errorContext)(testEndpoint) match {
          case Validated.Valid(_)        => fail(s"$testEndpoint should fail validation")
          case Validated.Invalid(errors) => errors.head shouldBe errorMessage
        }
      }
    }

    "not detect duplicate parameter names" in new Setup {
      val values = Table(
        ("URIPattern", "Query params"),
        ("/{alpha}", List.empty),  // No query parameters
        ("/alpha", List("alpha")), // No path parameters
        ("/{alpha}", List("beta")) // Different path and query parameters
      )

      forAll(values) { case (uriPattern, queryParams) =>
        val testEndpoint: Endpoint = endpoint.copy(
          uriPattern,
          queryParameters = queryParams.map(QueryParameter(_, required = true))
        )

        validator.validate("error context")(testEndpoint) match {
          case Validated.Valid(_)        => succeed
          case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
        }
      }
    }
  }
}
