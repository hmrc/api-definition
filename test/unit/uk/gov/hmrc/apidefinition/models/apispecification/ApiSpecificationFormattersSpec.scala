/*
 * Copyright 2020 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.models.apispecification

import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.apidefinition.models.apispecification.ExampleSpec

class ApiSpecificationFormattersSpec extends UnitSpec {

  import uk.gov.hmrc.apidefinition.models.apispecification.ApiSpecificationFormatters._

  "ApiSpecificationFormatters for ExampleSpec" should {
    "handle Some(empty string)" in {
      val es = ExampleSpec(None,None,Some(""), None)
      val jsonText: String = Json.prettyPrint(Json.toJson(es))
      jsonText.contains(""""code" : """") shouldBe true
    }
    "handle the reading of json" in {
      val jsonText = """
          |{
          |  "description" : "some text",
          |  "code" : ""
          |}
          """.stripMargin
      val es: ExampleSpec = Json.fromJson[ExampleSpec](Json.parse(jsonText)).asOpt.get
      es.code shouldBe Some("")
      es.description shouldBe Some("some text")
    }
  }
}