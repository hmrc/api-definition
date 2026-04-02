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
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.Endpoint
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.AuthType
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ResourceThrottlingTier
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.HttpMethod

class ApiVersionValidatorSpec extends AbstractValidatorSpec {
  "ApiVersionValidator" when {

    "validateVersionNumber" should {
      def validates(versionNbr: ApiVersionNbr)(implicit pos: Position): Unit = super.validates(ApiVersionValidator.validateVersionNumber(versionNbr), clue = Some(versionNbr.value))

      def failsToValidateVersionNbr(versionNbr: ApiVersionNbr)(implicit pos: Position): Unit =
        failsToValidate(ApiVersionValidator.validateVersionNumber(versionNbr)).head shouldBe "Field 'versions.version' is required"

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
      def validates(endpointsEnabled: Boolean, status: ApiStatus)(implicit pos: Position): Unit = super.validates(ApiVersionValidator.validateStatusAndEndpointsEnabled(endpointsEnabled, status), clue = Some(s"""Should be invalid for a status of $status to be ${if(endpointsEnabled) "enabled" else "disabled"}"""))
      def failsToValidateCombo(endpointsEnabled: Boolean, status: ApiStatus)(implicit pos: Position): Unit =
        failsToValidate(ApiVersionValidator.validateStatusAndEndpointsEnabled(endpointsEnabled, status)).head shouldBe "Field 'versions.endpointsEnabled' must be false for ALPHA status"

      "succeed for alpha when disabled" in {
        validates(false, ApiStatus.ALPHA)
      }
      
      "fail for alpha when enabled" in {
        failsToValidateCombo(true, ApiStatus.ALPHA)
      }

      "succeed for others when enabled" in {
        for {
          status <- List(ApiStatus.BETA, ApiStatus.STABLE, ApiStatus.DEPRECATED, ApiStatus.RETIRED)
          bool <- List(true, false)
          _ = validates(bool, status)
        } yield ()
      }
      
    }

    "validateAllEndpoints" should {
      def validate(endpoints: List[Endpoint])(implicit pos: Position): Unit = super.validates(ApiVersionValidator.validateAllEndpoints(endpoints))
      def failsToValidate(endpoints: List[Endpoint])(implicit pos: Position): Unit = super.failsToValidate(ApiVersionValidator.validateAllEndpoints(endpoints))

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
        validate(List(goodEndpoint1, goodEndpoint2))
      }

      "fail for endpoint with duff uri pattern" in {
        failsToValidate(List(goodEndpoint1, goodEndpoint2, badEndpoint))
      }
    }

    "validateUniqueEndpointPaths" should {
      def validate(uris: String*)(implicit pos: Position): Unit = super.validates(ApiVersionValidator.validateUniqueEndpointPaths(uris.toList))
      def failsToValidate(uris: String*)(implicit pos: Position): Unit = super.failsToValidate(ApiVersionValidator.validateUniqueEndpointPaths(uris.toList))

      
      "succeed for a single endpoint" in {
        validate("/a/good/uri")
      }

      "succeed for a two endpoint" in {
        validate("/a/good/uri", "/b/good/uri")
      }

      "fail for overlaps" in {
        failsToValidate("/a/good/uri", "/a/good/{bad}")





      }
    }

    /*
        "detect ambiguity of different variables in the same path segment" in new Setup {
      val values = Table(
        ("UriPattern1", "UriPattern2", "Error message"),
        (
          "/{alpha}",
          "/{beta}",
          "Ambiguous path segment variables for API 'Calendar API' version '1.0': List(The variables {alpha} and {beta} cannot appear in the same segment in the endpoints /{alpha} and /{beta})"
        ),
        (
          "/hello/{alpha}",
          "/hello/{beta}",
          "Ambiguous path segment variables for API 'Calendar API' version '1.0': List(The variables {alpha} and {beta} cannot appear in the same segment in the endpoints /hello/{alpha} and /hello/{beta})"
        )
      )

      forAll(values) { case (uriPattern1, uriPattern2, errorMessage) =>
        val apiDefinition: StoredApiDefinition = calendarApi.copy(versions =
          List(calendarApi.versions.head.copy(
            endpoints = List(
              Endpoint(uriPattern1, "Endpoint1", HttpMethod.GET, AuthType.NONE),
              Endpoint(uriPattern2, "Endpoint2", HttpMethod.GET, AuthType.NONE)
            )
          ))
        )

        assertValidationFailure(apiDefinition, List(errorMessage))
      }
    }

    "not detect ambiguity of different variables in the same path segment" in new Setup {
      val values = Table(
        ("UriPattern1", "UriPattern2"),
        ("/{alpha}/", "/world"),                           // Only one URI has a variable in root segment
        ("/hello/{alpha}/", "/hello/world"),               // Only one URI has a variable in segment 2
        ("/hello/{alpha}/{beta}", "/hello/world/{gamma}"), // OK up to segment 2, so variables in segment 3 don't matter
        ("/hello/world/{alpha}/", "/hello/World/{beta}")   // Different cases for 'world', so variables in segment 3 don't matter
      )

      forAll(values) { case (uriPattern1, uriPattern2) =>
        val apiDefinition: StoredApiDefinition = calendarApi.copy(versions =
          List(calendarApi.versions.head.copy(
            endpoints = List(
              Endpoint(uriPattern1, "Endpoint1", HttpMethod.GET, AuthType.NONE),
              Endpoint(uriPattern2, "Endpoint2", HttpMethod.GET, AuthType.NONE)
            )
          ))
        )

        assertValidationSuccess(apiDefinition)
      }
    }
  }
    */
  }
}
