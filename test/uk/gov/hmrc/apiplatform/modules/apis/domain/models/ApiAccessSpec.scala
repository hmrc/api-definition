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
import play.api.libs.json.Json

class ApiAccessSpec extends BaseJsonFormattersSpec {
  
  "ApiAccess" should {
    "read public access from Json" in {
      testFromJson[ApiAccess]("""{ "type": "PUBLIC"}""")(ApiAccess.PUBLIC)
    }

    "read private access from Json" in {
      testFromJson[ApiAccess]("""{ "type": "PRIVATE"}""")(ApiAccess.Private(List(), None))
    }

    "read private access with fields from Json" in {
      testFromJson[ApiAccess]("""{ "type": "PRIVATE", "whitelistedApplicationIds": ["123"], "isTrial": true}""")(ApiAccess.Private(List("123"), Some(true)))
    }

    "write to Json" in {
      Json.toJson[ApiAccess](ApiAccess.PUBLIC) shouldBe Json.obj("type" -> "PUBLIC")
    }
  }
}
