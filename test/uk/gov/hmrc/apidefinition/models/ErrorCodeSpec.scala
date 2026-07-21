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
    import ErrorCode.*
    val values =
      Table(
        ("Type", "Json"),
        (InvalidRequestPayload, "INVALID_REQUEST_PAYLOAD"),
        (InternalServerError, "INTERNAL_SERVER_ERROR"),
        (ApiDefinitionNotFound, "API_DEFINITION_NOT_FOUND"),
        (ApiInvalidJson, "API_INVALID_JSON"),
        (ContextAlreadyDefined, "CONTEXT_ALREADY_DEFINED"),
        (UnsupportedAccessType, "UNSUPPORTED_ACCESS_TYPE")
      )

    "convert lower case string to case object" in {
      forAll(values) { (s, _) =>
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
      forAll(values) { (s, j) =>
        testFromJson[ErrorCode](s""""$j"""")(s)
      }
    }

    "read with error from Json" in {
      intercept[Exception] {
        testFromJson[ErrorCode](s"""123""")(ErrorCode.ApiDefinitionNotFound)
      }.getMessage() should include("Cannot parse Error Code from '123'")
    }

    "write to Json" in {
      forAll(values) { (s, t) =>
        Json.toJson[ErrorCode](s) shouldBe JsString(t)
      }
    }
  }
}
