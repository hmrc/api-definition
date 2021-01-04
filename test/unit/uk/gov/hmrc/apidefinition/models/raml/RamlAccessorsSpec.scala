/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.models.raml

import org.scalatest.{WordSpec, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.gov.hmrc.apidefinition.raml.{SafeValueAsString, SafeValue}

class RamlAccessorsSpec extends WordSpec with Matchers {

  "SafeValue" should {
    "allow access to java nullable string" in {
      val inputs = Table(
        ("object", "expected"),
        (null, None),
        ("", None),
        ("abc", Some("abc"))
      )

      forAll(inputs) { (obj, expect) =>
        SafeValue(obj) shouldBe expect
      }
    }

    "allow access to java nullable value fields" in {
      case class Fake(v: String) {
        def value(): String = v
      }

      val inputs = Table(
        ("object", "expected"),
        (null, None),
        (new Fake(null), None),
        (new Fake(""), None),
        (new Fake("abc"), Some("abc"))
      )

      forAll(inputs) { (obj, expect) =>
        SafeValue(obj) shouldBe expect
      }
    }
  }

  "SafeValueAsString" should {
    "allow access to java nullable string" in {
      val inputs = Table(
        ("object", "expected"),
        (null, ""),
        ("", ""),
        ("abc", "abc")
      )

      forAll(inputs) { (obj, expect) =>
        SafeValueAsString(obj) shouldBe expect
      }
    }

    "allow access to java nullable value fields" in {
      case class Fake(v: String) {
        def value(): String = v
      }

      val inputs = Table(
        ("object", "expected"),
        (null, ""),
        (new Fake(null), ""),
        (new Fake(""), ""),
        (new Fake("abc"), "abc")
      )

      forAll(inputs) { (obj, expect) =>
        SafeValueAsString(obj) shouldBe expect
      }
    }
  }
}    
