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

import play.api.libs.json.{JsString, Json}

import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class AWSParameterTypeSpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks {

  "AWSParameterType" should {
    val values =
      Table(
        ("Type", "text"),
        (AWSParameterType.QUERY, "query"),
        (AWSParameterType.PATH, "path")
      )

    "convert to string correctly" in {
      forAll(values) { (s, t) =>
        s.toString() shouldBe t.toUpperCase()
      }
    }

    "convert lower case string to case object" in {
      forAll(values) { (s, t) =>
        AWSParameterType.apply(t) shouldBe Some(s)
        AWSParameterType.unsafeApply(t) shouldBe s
      }
    }

    "convert mixed case string to case object" in {
      forAll(values) { (s, t) =>
        AWSParameterType.apply(t.toUpperCase()) shouldBe Some(s)
        AWSParameterType.unsafeApply(t.toUpperCase()) shouldBe s
      }
    }

    "convert string value to None when undefined or empty" in {
      AWSParameterType.apply("rubbish") shouldBe None
      AWSParameterType.apply("") shouldBe None
    }

    "throw when string value is invalid" in {
      intercept[RuntimeException] {
        AWSParameterType.unsafeApply("rubbish")
      }.getMessage() should include("AWS Parameter Type")
    }

    "read from Json" in {
      forAll(values) { (s, t) =>
        testFromJson[AWSParameterType](s""""$t"""")(s)
      }
    }

    "read with error from Json" in {
      intercept[Exception] {
        testFromJson[AWSParameterType](s"""123""")(AWSParameterType.QUERY)
      }.getMessage() should include("Cannot parse AWS Parameter Type from '123'")
    }

    "write to Json" in {
      forAll(values) { (s, t) =>
        Json.toJson[AWSParameterType](s) shouldBe JsString(t.toUpperCase())
      }
    }
  }
}
