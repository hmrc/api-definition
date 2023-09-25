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

import org.scalatest.prop.TableDrivenPropertyChecks

import play.api.libs.json.{JsString, Json}

import uk.gov.hmrc.apiplatform.modules.common.utils._
import uk.gov.hmrc.apidefinition.models.TolerantJsonApiStatus

class TolerantJsonApiStatusSpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks with TolerantJsonApiStatus {

  "ApiStatus" should {
    val values =
      Table(
        ("Status", "text"),
        (ApiStatus.ALPHA, "Alpha"),
        (ApiStatus.BETA, "Beta"),
        (ApiStatus.STABLE, "Stable"),
        (ApiStatus.DEPRECATED, "Deprecated"),
        (ApiStatus.RETIRED, "Retired")
      )

    "display text correctly" in {
      forAll(values) { (s, t) =>
        s.displayText shouldBe t
      }
    }

    "convert to string correctly" in {
      forAll(values) { (s, t) =>
        s.toString() shouldBe t.toUpperCase()
      }
    }

    "convert lower case string to case object" in {
      forAll(values) { (s, t) =>
        ApiStatus.apply(t) shouldBe Some(s)
        ApiStatus.unsafeApply(t) shouldBe s
      }
    }

    "convert mixed case string to case object" in {
      forAll(values) { (s, t) =>
        ApiStatus.apply(t.toUpperCase()) shouldBe Some(s)
        ApiStatus.unsafeApply(t.toUpperCase()) shouldBe s
      }
    }

    "convert string value to None when undefined or empty" in {
      ApiStatus.apply("rubbish") shouldBe None
      ApiStatus.apply("") shouldBe None
    }

    "throw when string value is invalid" in {
      intercept[RuntimeException] {
        ApiStatus.unsafeApply("rubbish")
      }.getMessage() should include("API Status")
    }

    "read from Json" in {
      forAll(values) { (s, t) =>
        testFromJson[ApiStatus](s""""$t"""")(s)
      }
    }

    "read PUBLISHED from Json" in {
      testFromJson[ApiStatus](""" "PUBLISHED" """)(ApiStatus.STABLE)
    }

    "read PROTOTYPE from Json" in {
      testFromJson[ApiStatus](""" "PROTOTYPE" """)(ApiStatus.BETA)
    }

    "write to Json" in {
      forAll(values) { (s, t) =>
        Json.toJson[ApiStatus](s) shouldBe JsString(t.toUpperCase())
      }
    }
  }
}
