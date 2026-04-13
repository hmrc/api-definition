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

package uk.gov.hmrc.apidefinition.models

import org.scalatest.prop.TableDrivenPropertyChecks

import play.api.libs.json.Format
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class TolerantJsonApiAccessSpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks {

  implicit val useMe: Format[ApiAccess] = TolerantJsonApiAccess.tolerantFormatApiAccess

  val appId = ApplicationId.random

  "TolerantJsonApiAccess" should {

    "read public, controlled and internal access from Json" in {
      val values =
        Table(
          ("JSON", "Access"),
          ("PUBLIC", ApiAccess.PUBLIC),
          ("CONTROLLED", ApiAccess.CONTROLLED),
          ("INTERNAL", ApiAccess.INTERNAL)
        )
      forAll(values) { (json: String, access: ApiAccess) =>
        testFromJson[ApiAccess](s"""{ "type": "$json"}""")(access)
      }
    }

    "read private access from Json even without any fields" in {
      testFromJson[ApiAccess]("""{ "type": "PRIVATE"}""")(ApiAccess.INTERNAL)
    }

    "read private access with fields from Json" in {
      testFromJson[ApiAccess](s"""{ "type": "PRIVATE", "isTrial": true}""")(ApiAccess.CONTROLLED)
    }

    "read private access with fields from Json with false" in {
      testFromJson[ApiAccess](s"""{ "type": "PRIVATE", "isTrial": false}""")(ApiAccess.INTERNAL)
    }

    "fail to read private access from Json when bad isTrial type" in {
      intercept[RuntimeException] {
        testFromJson[ApiAccess]("""{ "type": "PRIVATE",  "isTrial": "bob" }""")(ApiAccess.INTERNAL)
      }
    }

    "write public, controlled and internal access to Json" in {
      val values =
        Table(
          ("Access", "JSON"),
          (ApiAccess.PUBLIC, "PUBLIC"),
          (ApiAccess.CONTROLLED, "CONTROLLED"),
          (ApiAccess.INTERNAL, "INTERNAL"),
          (ApiAccess.Private(true), "CONTROLLED"),
          (ApiAccess.Private(false), "INTERNAL")
        )
      forAll(values) { (access: ApiAccess, json: String) =>
        testToJson(access)("type" -> json)
      }
    }
  }
}
