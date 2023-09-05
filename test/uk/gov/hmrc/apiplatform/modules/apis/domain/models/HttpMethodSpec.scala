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

class HttpMethodSpec extends BaseJsonFormattersSpec with TableDrivenPropertyChecks {
  
  "HttpMethod" should {
    val values =
      Table(
        ("Method", "text"),
        ( HttpMethod.GET, "get"),
        ( HttpMethod.POST, "post"),
        ( HttpMethod.PUT, "put"),
        ( HttpMethod.PATCH, "patch"),
        ( HttpMethod.DELETE, "delete"),
        ( HttpMethod.OPTIONS, "options"),
        ( HttpMethod.HEAD, "head")
      )

    "convert to string correctly" in {
      forAll(values) { (s,t) =>
        s.toString() shouldBe t.toUpperCase()
      }
    }

    "convert lower case string to case object" in {
      forAll(values) { (s, t) => 
        HttpMethod.apply(t) shouldBe Some(s)
        HttpMethod.unsafeApply(t) shouldBe s
      }
    }

    "convert mixed case string to case object" in {
      forAll(values) { (s, t) => 
        HttpMethod.apply(t.toUpperCase()) shouldBe Some(s)
        HttpMethod.unsafeApply(t.toUpperCase()) shouldBe s
      }
    }

    "convert string value to None when undefined or empty" in {
      HttpMethod.apply("rubbish") shouldBe None
      HttpMethod.apply("") shouldBe None
    }
      
    "throw when string value is invalid" in {
      intercept[RuntimeException] {
        HttpMethod.unsafeApply("rubbish")
      }.getMessage() should include ("Http Method")
    }

    "read from Json" in {
      forAll(values) { (s, t) =>
        testFromJson[HttpMethod](s""""$t"""")(s)
      }
    }

    "write to Json" in {
      forAll(values) { (s, t) =>
        Json.toJson[HttpMethod](s) shouldBe JsString(t.toUpperCase())
      }
    }
  }
}

