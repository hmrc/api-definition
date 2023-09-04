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

class ApiVersionSourceSpec extends BaseJsonFormattersSpec {

  "ApiVersionSource" should {
    "convert string value to enum with lowercase" in {
      ApiVersionSource.apply("raml") shouldBe Some(ApiVersionSource.RAML)
      ApiVersionSource.apply("oas") shouldBe Some(ApiVersionSource.OAS)
      ApiVersionSource.apply("unknown") shouldBe Some(ApiVersionSource.UNKNOWN)

      ApiVersionSource.unsafeApply("raml") shouldBe ApiVersionSource.RAML
      ApiVersionSource.unsafeApply("oas") shouldBe ApiVersionSource.OAS
      ApiVersionSource.unsafeApply("unknown") shouldBe ApiVersionSource.UNKNOWN
    }

    "convert string value to enum with mixed case" in {
      ApiVersionSource.apply("Raml") shouldBe Some(ApiVersionSource.RAML)
      ApiVersionSource.apply("Oas") shouldBe Some(ApiVersionSource.OAS)
      ApiVersionSource.apply("Unknown") shouldBe Some(ApiVersionSource.UNKNOWN)
    }

    "convert string value to None when undefined or empty" in {
      ApiVersionSource.apply("rubbish") shouldBe None
      ApiVersionSource.apply("") shouldBe None
    }
    
    "throw when string value is invalid" in {
      intercept[RuntimeException] {
        ApiVersionSource.unsafeApply("rubbish") shouldBe None
      }
    }

    val anApiVersionSource: ApiVersionSource = ApiVersionSource.OAS

    "convert to json" in {
      Json.toJson(anApiVersionSource) shouldBe JsString(anApiVersionSource.asText)
    }

    "read from json" in {
      testFromJson[ApiVersionSource](s""""${anApiVersionSource.asText}"""")(anApiVersionSource)
    }
  }
}
