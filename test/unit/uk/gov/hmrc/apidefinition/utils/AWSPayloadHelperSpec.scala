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

package unit.uk.gov.hmrc.apidefinition.utils

import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.utils.AWSPayloadHelper._
import uk.gov.hmrc.play.test.UnitSpec

class AWSPayloadHelperSpec extends UnitSpec {

  private val endpoint = Endpoint(
    uriPattern = "",
    endpointName = "",
    method = HttpMethod.GET,
    authType = AuthType.NONE,
    throttlingTier = ResourceThrottlingTier.UNLIMITED,
    scope = None,
    queryParameters = None)

  private val endpointsWithScopes = Seq(
    endpoint,
    endpoint.copy(scope = Some("my-scope")),
    endpoint.copy(scope = Some("a-scope")),
    endpoint.copy(scope = Some("great-scope")),
    endpoint.copy(scope = Some("a-scope")))

  private val queryParameters = Seq(
    Parameter(name = "city"),
    Parameter(name = "address", required = true),
    Parameter(name = "postcode", required = true)
  )

  private val endpointWithQueryParameters = endpoint.copy(queryParameters = Some(queryParameters))

  private val endpointWithPathParameters = endpoint.copy(uriPattern = "/hello/{surname}/{nickname}")

  "buildAWSPathParameters()" should {

    "return an empty sequence if there are no path parameters" in {
      buildAWSPathParameters(endpoint.copy(uriPattern = "/hello/world")) shouldBe Seq()
    }

    "return all path parameters sorted by segment precedence" in {
      val expectedPathParameters = Seq(
        AWSPathParameter(name = "surname"),
        AWSPathParameter(name = "nickname"))

      buildAWSPathParameters(endpointWithPathParameters) shouldBe expectedPathParameters
    }
  }

  "buildAWSQueryParameters()" should {

    "return an empty sequence if there are no query parameters" in {
      buildAWSQueryParameters(endpoint) shouldBe Seq()
    }

    "return all query parameters sorted by name" in {
      val expectedQueryParameters = Seq(
        AWSQueryParameter(name = "address", required = true),
        AWSQueryParameter(name = "city", required = false),
        AWSQueryParameter(name = "postcode", required = true))

      buildAWSQueryParameters(endpointWithQueryParameters) shouldBe expectedQueryParameters
    }
  }

  "buildAWSParameters()" should {

    "return None if there are no query parameters, nor path parameters" in {
      buildAWSParameters(endpoint) shouldBe None
    }

    "return path parameters first and then the query parameters sorted alphabetically" in {
      val endpointWithManyParams = endpointWithQueryParameters.copy(uriPattern = "/hello/{surname}/{nickname}")

      val expectedParameters = Seq(
        AWSPathParameter(name = "surname"),
        AWSPathParameter(name = "nickname"),
        AWSQueryParameter(name = "address", required = true),
        AWSQueryParameter(name = "city", required = false),
        AWSQueryParameter(name = "postcode", required = true))

      buildAWSParameters(endpointWithManyParams) shouldBe Some(expectedParameters)
    }
  }

}
