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

package uk.gov.hmrc.apidefinition.models.apispecification

import play.api.libs.json._

import uk.gov.hmrc.apidefinition.models.apispecification.EnumerationValue._
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class EnumerationFormatterSpec extends AsyncHmrcSpec {

  "EnumerationValue formatter" should {
    "handle JsValues correctly" in {
      Json.fromJson[EnumerationValue](JsNumber(1)) shouldBe JsSuccess(EnumerationValue("1"))
      Json.fromJson[EnumerationValue](JsString("hello")) shouldBe JsSuccess(EnumerationValue("hello"))
      Json.fromJson[EnumerationValue](JsBoolean(true)) shouldBe JsSuccess(EnumerationValue("true"))
      Json.fromJson[EnumerationValue](JsArray(Seq(JsNumber(1)))) shouldBe JsError("Unsupported enum format (Json array): use NUMBER, BOOLEAN OR STRING")
      Json.fromJson[EnumerationValue](JsObject.empty) shouldBe JsError("Unsupported enum format (Json object): use NUMBER, BOOLEAN OR STRING")
      Json.fromJson[EnumerationValue](JsNull) shouldBe JsError("Unsupported enum format (Json null): use NUMBER, BOOLEAN OR STRING")
    }
  }
}
