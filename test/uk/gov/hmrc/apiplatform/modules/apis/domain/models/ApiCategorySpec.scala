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

import play.api.libs.json.Json
import play.api.libs.json.JsString
import uk.gov.hmrc.apiplatform.modules.common.utils._
import org.scalatest.prop.TableDrivenPropertyChecks

class ApiCategorySpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks {

  "ApiCategorySpec" should {
    val values =
      Table(
        ("Category", "text"),
        ( ApiCategory.AGENTS, "agents"),
        ( ApiCategory.BUSINESS_RATES, "business_rates")
      )

    "convert to string correctly" in {
      forAll(values) { (s,t) =>
        s.toString() shouldBe t.toUpperCase()
      }
    }

    "convert lower case string to case object" in {
      forAll(values) { (s, t) => 
        ApiCategory.apply(t) shouldBe Some(s)
        ApiCategory.unsafeApply(t) shouldBe s
      }
    }

    "convert mixed case string to case object" in {
      forAll(values) { (s, t) => 
        ApiCategory.apply(t.toUpperCase()) shouldBe Some(s)
        ApiCategory.unsafeApply(t.toUpperCase()) shouldBe s
      }
    }

    "convert string value to None when undefined or empty" in {
      ApiCategory.apply("rubbish") shouldBe None
      ApiCategory.apply("") shouldBe None
    }
      
    "throw when string value is invalid" in {
      intercept[RuntimeException] {
        ApiCategory.unsafeApply("rubbish")
      }.getMessage() should include ("API Category")
    }

    "read from Json" in {
      forAll(values) { (s, t) =>
        testFromJson[ApiCategory](s""""$t"""")(s)
      }
    }

    "write to Json" in {
      forAll(values) { (s, t) =>
        Json.toJson[ApiCategory](s) shouldBe JsString(t.toUpperCase())
      }
    }
  }
}

