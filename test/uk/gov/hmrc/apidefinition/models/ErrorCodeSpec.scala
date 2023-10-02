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

class ErrorCodeSpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks {

  // Simpler than most tests - no real toString overriding or displayText
  
  "ErrorCode" should {
    import ErrorCode._
    val values =
      Table(
        ("Type"),
        (INVALID_REQUEST_PAYLOAD ),
        (INTERNAL_SERVER_ERROR   ),   
        (API_DEFINITION_NOT_FOUND),
        (API_INVALID_JSON        ),  
        (CONTEXT_ALREADY_DEFINED ),
        (UNSUPPORTED_ACCESS_TYPE )
      )

    "convert lower case string to case object" in {
      forAll(values) { (s) =>
        ErrorCode.apply(s.toString().toLowerCase()) shouldBe Some(s)
        ErrorCode.unsafeApply(s.toString().toLowerCase()) shouldBe s
      }
    }

    "convert string value to None when undefined or empty" in {
      ErrorCode.apply("rubbish") shouldBe None
      ErrorCode.apply("") shouldBe None
    }

    "throw when string value is invalid" in {
      intercept[RuntimeException] {
        ErrorCode.unsafeApply("rubbish")
      }.getMessage() should include("Error Code")
    }

    "read from Json" in {
      forAll(values) { (s) =>
        val t = s.toString()
        testFromJson[ErrorCode](s""""$t"""")(s)
      }
    }

    "read with error from Json" in {
      intercept[Exception] {
        testFromJson[ErrorCode](s"""123""")(ErrorCode.API_DEFINITION_NOT_FOUND)
      }.getMessage() should include("Cannot parse Error Code from '123'")
    }

    "write to Json" in {
      forAll(values) { (s) =>
        Json.toJson[ErrorCode](s) shouldBe JsString(s.toString())
      }
    }
  }
}
