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
import uk.gov.hmrc.apidefinition.models.TolerantJsonApiAccess

class TolerantJsonApiAccessSpec extends BaseJsonFormattersSpec {

  implicit val useMe = TolerantJsonApiAccess.tolerantFormatApiAccess

  "TolerantJsonApiAccess" should {

    "read public access from Json" in {
      testFromJson[ApiAccess]("""{ "type": "PUBLIC"}""")(ApiAccess.PUBLIC)
    }

    "read private access from Json even without any fields" in {
      testFromJson[ApiAccess]("""{ "type": "PRIVATE"}""")(ApiAccess.Private(List(), false))
    }

    "read private access from Json even without isPrivateTrial" in {
      testFromJson[ApiAccess]("""{ "type": "PRIVATE",  "whitelistedApplicationIds": ["123"]}""")(ApiAccess.Private(List("123"), false))
    }

    "read private access with fields from Json" in {
      testFromJson[ApiAccess]("""{ "type": "PRIVATE", "whitelistedApplicationIds": ["123"], "isTrial": true}""")(ApiAccess.Private(List("123"), true))
    }

    "fail to read private access from Json when bad whitelist type" in {
      intercept[RuntimeException] {
        testFromJson[ApiAccess]("""{ "type": "PRIVATE",  "whitelistedApplicationIds": [999]}""")(ApiAccess.Private(List(), false))
      }
    }
    "fail to read private access from Json when bad isTrial type" in {
      intercept[RuntimeException] {
        testFromJson[ApiAccess]("""{ "type": "PRIVATE",  "isTrial": "bob" }""")(ApiAccess.Private(List(), false))
      }
    }
  }
}
