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

package uk.gov.hmrc.apiplatform.modules.apis.domain.models

import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

import uk.gov.hmrc.apidefinition.models.TolerantJsonEndpoint

class TolerantJsonEndpointSpec extends BaseJsonFormattersSpec {

  implicit val useMe = TolerantJsonEndpoint.tolerantFormatEndpoint
  "TolerantJsonEndpoint" should {

    "read endpoint from Json" in {
      val json = """{
                   |  "uriPattern":"/today",
                   |  "endpointName":"Get Today's Date",
                   |  "method":"GET",
                   |  "authType":"NONE",
                   |  "throttlingTier":"UNLIMITED"
                   |}""".stripMargin

      testFromJson[Endpoint](json)(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED, None, List.empty))
    }

    "read endpoint from Json with all fields" in {
      val json = """{
                   |  "uriPattern":"/today",
                   |  "endpointName":"Get Today's Date",
                   |  "method":"GET",
                   |  "authType":"NONE",
                   |  "throttlingTier":"UNLIMITED",
                   |  "scope": "hello",
                   |  "queryParameters": []
                   |}""".stripMargin

      testFromJson[Endpoint](json)(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED, Some("hello"), List.empty))
    }

    "fail to read private access from Json when bad scope type" in {
      val json = """{
                   |  "uriPattern":"/today",
                   |  "endpointName":"Get Today's Date",
                   |  "method":"GET",
                   |  "authType":"NONE",
                   |  "throttlingTier":"UNLIMITED",
                   |  "scope": 123,
                   |  "queryParameters": []
                   |}""".stripMargin

      val ignoreMe = Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED, None, List.empty)

      intercept[RuntimeException] {
        testFromJson[Endpoint](json)(ignoreMe)
      }
    }

    "fail to read private access from Json when bad query parameters type" in {
      val json = """{
                   |  "uriPattern":"/today",
                   |  "endpointName":"Get Today's Date",
                   |  "method":"GET",
                   |  "authType":"NONE",
                   |  "throttlingTier":"UNLIMITED",
                   |  "queryParameters": [ "123" ]
                   |}""".stripMargin

      val ignoreMe = Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED, None, List.empty)

      intercept[RuntimeException] {
        testFromJson[Endpoint](json)(ignoreMe)
      }
    }
  }
}
