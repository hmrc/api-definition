/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalactic.source.Position

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{AuthType, Endpoint, HttpMethod, QueryParameter, ResourceThrottlingTier}

class ApiEndpointValidatorSpec extends AbstractValidatorSpec {
  "ApiEndpointValidator" when {
    "validateEndpointName" should {
      def validates(name: String)(implicit pos: Position): Unit                 = super.validates(ApiEndpointValidator.validateEndpointName(name))
      def failsToValidateWithNoName(name: String)(implicit pos: Position): Unit =
        failsToValidate(ApiEndpointValidator.validateEndpointName(name))("Field 'endpoints.endpointName' is required")

      "detect an empty name" in {
        failsToValidateWithNoName("")
      }

      "detect a blank name" in {
        failsToValidateWithNoName("   ")
      }

      "detect a valid name" in {
        validates("abc")
        validates("ab-c")
        validates("a_bc")
        validates("a_12bc")
        validates("a_12bc")
      }
    }

    "validateUriPattern" should {
      def validates(value: String, clue: Option[String] = Some(s"should validate $value"))(implicit pos: Position): Unit =
        super.validates(ApiEndpointValidator.validateUriPattern(value), clue = clue)
      def failsToValidateWithEmptyValue(value: String)(implicit pos: Position): Unit                                     =
        failsToValidate(ApiEndpointValidator.validateUriPattern(value))("Field 'endpoints.uriPattern' is required")
      def failsToValidateWithBadValue(value: String, clue: Option[String])(implicit pos: Position): Unit                 = failsToValidate(
        ApiEndpointValidator.validateUriPattern(value),
        clue = clue
      )(s"Field 'endpoints.uriPattern' with value '$value' should match regular expression")

      "detect an empty uri pattern" in {
        failsToValidateWithEmptyValue("")
      }

      "detect a blank uri pattern" in {
        failsToValidateWithEmptyValue("   ")
      }

      "detect an invalid uri pattern" in {
        failsToValidateWithBadValue("/well.known", clue = Some("not allow dots in middle of endpoints"))

        val specialChars = List(
          ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
          '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
        )

        specialChars.foreach { char: Char =>
          val endpointUri = s"/payments$char"
          failsToValidateWithBadValue(endpointUri, clue = Some(s"Invalid char '$char' should not be allowed"))
        }
      }

      "detect a valid uri pattern" in {
        validates("/.well-known/openid-configuration", clue = Some("allow dots at start of endpoints"))
        validates("/well-known/openid-configuration")
        validates("/paye/{nino}/eligibility-check-digitally-excluded")
      }

    }

    "validateScope" should {
      def failsToValidateWithNoValue(value: Option[String])(implicit pos: Position): Unit =
        failsToValidate(ApiEndpointValidator.validateScope(value))("Field 'endpoints.scope' is required")

      "detect an empty name" in {
        failsToValidateWithNoValue(Some(""))
      }

      "detect a blank name" in {
        failsToValidateWithNoValue(Some("   "))
      }

      "detect a None" in {
        failsToValidateWithNoValue(None)
      }

      "detect a valid name" in {
        validates(ApiEndpointValidator.validateScope(Some("abc")))
      }
    }

    "validateQueryParameters" should {
      val validNames   = List("abc", "def")
      val invalidNames = List(" ", "", "abc!")

      def failsToValidate(names: List[String], numberOfErrors: Int = 1)(implicit pos: Position): Unit =
        super.failsToValidate(ApiEndpointValidator.validateQueryParameters(names.map(QueryParameter(_))), numberOfErrors)()

      "succeed when all parameters are valid" in {
        validates(ApiEndpointValidator.validateQueryParameters(validNames.map(QueryParameter(_))), clue = Some(s"Valid names of ${validNames.mkString}"))
      }

      "fail when one parameter is invalid" in {
        failsToValidate(invalidNames.head :: validNames)
      }

      "fail when many parameters are invalid" in {
        failsToValidate(invalidNames ++ validNames, 3)
      }
    }

    "validatePathParameters" should {
      def validates(uriPattern: String)(implicit pos: Position): Unit                                              =
        super.validates(ApiEndpointValidator.validatePathParameters(uriPattern), clue = Some(s"Valid uri pattern of $uriPattern"))
      def failsToValidate(uriPattern: String, numberOfErrors: Int = 1, clue: String)(implicit pos: Position): Unit =
        super.failsToValidate(ApiEndpointValidator.validatePathParameters(uriPattern), numberOfErrors, Some(clue))()

      "succeed for valid path params" in {
        validates("/abc/def")
        validates("/abc/{param1}/def")
        validates("/abc/{param1}/def/{param2}")
      }

      "fails for invalid path params" in {
        failsToValidate("/abc/{param1!}", clue = "No bangs should be allowed")
        failsToValidate("/abc/{param1!}/{param.two}", 2, clue = "Several problems with path parameters")
        failsToValidate("/abc/{param1!}/{param.two}/{goodParam}", 2, clue = "Several problems with path parameters even if one is good")
        failsToValidate("/abc/d}e{f", clue = "It's not a param but has brackets in")
      }
    }

    "validateUniqueParameterNames" should {
      def validates(uriPattern: String, queryParameters: List[QueryParameter])(implicit pos: Position): Unit                     =
        super.validates(ApiEndpointValidator.validateUniqueParameterNames(uriPattern, queryParameters), clue = Some(s"Valid uri pattern of $uriPattern"))
      def failsToValidate(uriPattern: String, queryParameters: List[QueryParameter], clue: String)(implicit pos: Position): Unit =
        super.failsToValidate(ApiEndpointValidator.validateUniqueParameterNames(uriPattern, queryParameters), clue = Some(clue))()

      val qp1 = QueryParameter("abc", false)
      val qp2 = QueryParameter("def", false)

      "succeed for valid combos" in {
        validates("/abc/{paramA}/{paramB}", List(qp1, qp2))
        validates("/abc/{paramA}/{paramB}", List(qp1, qp1)) // We allow duplicate query params
        validates("/abc/{paramA}/{paramA}", List(qp1, qp2)) // We allow duplicate path params
      }

      "fail for invalid combos" in {
        failsToValidate("/abc/{paramA}/{def}", List(qp1, qp2), "path and query param clash")
      }
    }

    "validate" should {
      def validates(endpoint: Endpoint)(implicit pos: Position): Unit = super.validates(ApiEndpointValidator.validate(endpoint))

      "collect all errors when failing" in {
        failsToValidate(
          ApiEndpointValidator.validate(
            Endpoint(
              uriPattern = "/bad.pattern/{paramA}/abc",
              endpointName = " ",
              method = HttpMethod.GET,
              authType = AuthType.USER,
              scope = None,
              throttlingTier = ResourceThrottlingTier.UNLIMITED,
              queryParameters = List(
                "paramA",
                " ",
                "",
                "okay"
              ).map(QueryParameter(_, false))
            )
          ),
          numberOfErrors = 6
        )()
      }

      "succeed with a good USER endpoint" in {
        validates(
          Endpoint(
            uriPattern = "/.goodpattern/{paramA}/abc",
            endpointName = "fred",
            method = HttpMethod.GET,
            authType = AuthType.USER,
            scope = Some("xyz"),
            throttlingTier = ResourceThrottlingTier.UNLIMITED,
            queryParameters = List(
              "param1",
              "param2",
              "param3"
            ).map(QueryParameter(_, false))
          )
        )
      }

      "succeed with a good APP endpoint" in {
        validates(
          Endpoint(
            uriPattern = "/.goodpattern/{paramA}/abc",
            endpointName = "fred",
            method = HttpMethod.GET,
            authType = AuthType.APPLICATION,
            scope = None,
            throttlingTier = ResourceThrottlingTier.UNLIMITED,
            queryParameters = List(
              "param1",
              "param2",
              "param3"
            ).map(QueryParameter(_, false))
          )
        )
      }
    }
  }
}
