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
import org.scalatest.prop.TableDrivenPropertyChecks

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._

class ApiVersionValidatorSpec extends AbstractValidatorSpec with TableDrivenPropertyChecks {
  "ApiVersionValidator" when {

    "validateVersionNumber" should {
      def validates(versionNbr: ApiVersionNbr)(implicit pos: Position): Unit =
        super.validates(ApiVersionValidator.validateVersionNumber(versionNbr), clue = Some(versionNbr.value))

      def failsToValidateVersionNbr(versionNbr: ApiVersionNbr)(implicit pos: Position): Unit =
        failsToValidate(ApiVersionValidator.validateVersionNumber(versionNbr))("Field 'versions.version' is required")

      "detect an empty version nbr" in {
        failsToValidateVersionNbr(ApiVersionNbr(""))
      }

      "detect a blank version nbr" in {
        failsToValidateVersionNbr(ApiVersionNbr("   "))
      }

      "succeed with a valid version nbr" in {
        validates(ApiVersionNbr("1.0"))
      }
    }

    "validateStatusAndEndpointsEnabled" should {
      def validates(endpointsEnabled: Boolean, status: ApiStatus)(implicit pos: Position): Unit            = super.validates(
        ApiVersionValidator.validateStatusAndEndpointsEnabled(endpointsEnabled, status),
        clue = Some(s"""Should be invalid for a status of $status to be ${if (endpointsEnabled) "enabled" else "disabled"}""")
      )
      def failsToValidateCombo(endpointsEnabled: Boolean, status: ApiStatus)(implicit pos: Position): Unit =
        failsToValidate(
          ApiVersionValidator.validateStatusAndEndpointsEnabled(endpointsEnabled, status)
        )("Field 'versions.endpointsEnabled' must be false for ALPHA status")

      "succeed for alpha when disabled" in {
        validates(false, ApiStatus.ALPHA)
      }

      "fail for alpha when enabled" in {
        failsToValidateCombo(true, ApiStatus.ALPHA)
      }

      "succeed for others when enabled" in {
        for {
          status <- List(ApiStatus.BETA, ApiStatus.STABLE, ApiStatus.DEPRECATED, ApiStatus.RETIRED)
          bool   <- List(true, false)
          _       = validates(bool, status)
        } yield ()
      }
    }

    "validateAllEndpoints" should {
      val goodEndpoint1 =
        Endpoint(
          uriPattern = "/.goodpattern/{paramA}/abc",
          endpointName = "fred",
          method = HttpMethod.GET,
          authType = AuthType.USER,
          scope = Some("xyz"),
          throttlingTier = ResourceThrottlingTier.UNLIMITED
        )

      val goodEndpoint2 = goodEndpoint1.copy(
        uriPattern = "/abc/def",
        endpointName = "bob"
      )

      val badEndpoint = goodEndpoint1.copy(
        uriPattern = "/bad.pattern/{paramA}/abc"
      )

      "succeed" in {
        validates(ApiVersionValidator.validateAllEndpoints(List(goodEndpoint1, goodEndpoint2)))
      }

      "fail for missing endpoints" in {
        failsToValidate(ApiVersionValidator.validateAllEndpoints(List.empty))("Field 'versions.endpoints' must not be empty")
      }

      "fail for endpoint with duff uri pattern" in {
        failsToValidate(ApiVersionValidator.validateAllEndpoints(List(goodEndpoint1, goodEndpoint2, badEndpoint)))(
          s"${badEndpoint.endpointName} - Field 'endpoints.uriPattern' with value '${badEndpoint.uriPattern}' should match regular expression"
        )
      }
    }

    "validateUniqueEndpointPaths" should {
      def validates(uris: String*)(implicit pos: Position): Unit = super.validates(ApiVersionValidator.validateUniqueEndpointPaths(uris.toList))
      "succeed when there is only one URI pattern" in {
        validates("/{alpha}")
      }

      "succeed when different variables in the same path segment are not ambiguous" in {
        val values = Table(
          ("UriPattern1", "UriPattern2"),
          ("/{alpha}/", "/world"),                           // Only one URI has a variable in root segment
          ("/hello/{alpha}/", "/hello/world"),               // Only one URI has a variable in segment 2
          ("/hello/{alpha}/{beta}", "/hello/world/{gamma}"), // OK up to segment 2, so variables in segment 3 don't matter
          ("/hello/world/{alpha}/", "/hello/World/{beta}")   // Different cases for 'world', so variables in segment 3 don't matter
        )

        forAll(values) { case (uriPattern1, uriPattern2) => validates(uriPattern1, uriPattern2) }
      }

      "succeed when there are three non-ambiguous URI patterns" in {
        validates("/{alpha}", "/world", "/hello/{alpha}")
      }

      "fail when different variables in the same path segment are ambiguous" in {
        val values = Table(
          ("UriPattern1", "UriPattern2"),
          ("/{alpha}", "/{beta}"),            // Different variables cannot appear in the first segment
          ("/hello/{alpha}", "/hello/{beta}") // Different variables cannot appear in the second segment
        )

        forAll(values) {
          case (uriPattern1, uriPattern2) => failsToValidate(ApiVersionValidator.validateUniqueEndpointPaths(List(uriPattern1, uriPattern2)))(
              s"Ambiguous path segment variables: The variables {alpha} and {beta} cannot appear in the same segment in the endpoints $uriPattern1 and $uriPattern2"
            )
        }
      }

      "fail when there are three URI patterns with two of them ambiguous" in {
        failsToValidate(ApiVersionValidator.validateUniqueEndpointPaths(List("/{alpha}", "/world", "/{beta}")))(
          "Ambiguous path segment variables: The variables {alpha} and {beta} cannot appear in the same segment in the endpoints /{alpha} and /{beta}"
        )
      }
    }
  }

  "validate" should {
    "collect all errors when failing" in {
      failsToValidate(
        ApiVersionValidator.validate(
          ApiVersion(
            versionNbr = ApiVersionNbr(""),
            status = ApiStatus.ALPHA,
            access = ApiAccess.PUBLIC,
            endpoints = List(
              Endpoint(
                uriPattern = "/bad.pattern/{paramA}/abc",
                endpointName = "",
                method = HttpMethod.GET,
                authType = AuthType.APPLICATION
              )
            ),
            endpointsEnabled = true,
            awsRequestId = None,
            versionSource = ApiVersionSource.UNKNOWN
          )
        ),
        numberOfErrors = 4
      )(
        "Version  - Field 'versions.version' is required",
        "Version  -  - Field 'endpoints.endpointName' is required",
        "Version  -  - Field 'endpoints.uriPattern' with value '/bad.pattern/{paramA}/abc' should match regular expression",
        "Version  - Field 'versions.endpointsEnabled' must be false for ALPHA status"
      )
    }

    "succeed with a good version" in {
      validates(ApiVersionValidator.validate(
        ApiVersion(
          versionNbr = ApiVersionNbr("1.0"),
          status = ApiStatus.ALPHA,
          access = ApiAccess.PUBLIC,
          endpoints = List(
            Endpoint(
              uriPattern = "/.goodpattern/{paramA}/abc",
              endpointName = "goodname",
              method = HttpMethod.GET,
              authType = AuthType.APPLICATION
            )
          ),
          endpointsEnabled = false,
          awsRequestId = None,
          versionSource = ApiVersionSource.UNKNOWN
        )
      ))
    }
  }
}
