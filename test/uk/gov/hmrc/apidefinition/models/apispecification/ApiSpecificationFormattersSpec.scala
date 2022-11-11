/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiSpecificationFormattersSpec extends AsyncHmrcSpec {

  import uk.gov.hmrc.apidefinition.models.apispecification.ApiSpecificationFormatters._

  "ApiSpecificationFormatters for ExampleSpec" should {
    "handle Some(empty string)" in {
      val es               = ExampleSpec(None, None, Some(""), None)
      val jsonText: String = Json.prettyPrint(Json.toJson(es))
      jsonText.contains(""""code" : """") shouldBe true
    }
    "handle the reading of json" in {
      val jsonText        =
        """
          |{
          |  "description" : "some text",
          |  "code" : ""
          |}
          """.stripMargin
      val es: ExampleSpec = Json.fromJson[ExampleSpec](Json.parse(jsonText)).asOpt.get
      es.code shouldBe Some("")
      es.description shouldBe Some("some text")
    }
    "Type Declaration" should {
      "handle writing empty lists" in {
        val td               = TypeDeclaration("Name", "Display Name", "Type", true, None, List(), List(), None)
        val jsonText: String = Json.prettyPrint(Json.toJson(td))
        jsonText.contains(""""examples" : """) shouldBe false
        jsonText.contains(""""enumValues" : """) shouldBe false
      }
      "handle writing non-empty lists" in {
        val td               = TypeDeclaration("Name", "Display Name", "Type", true, None, List(), List("Enum"), None)
        val jsonText: String = Json.prettyPrint(Json.toJson(td))
        jsonText.contains(""""enumValues" : [""") shouldBe true
      }
      "handle reading missing lists/values" in {
        val jsonText            =
          """{
            | "name": "Name",
            | "displayName":"Display Name",
            | "type": "Type",
            | "required": true
            |}""".stripMargin
        val es: TypeDeclaration = Json.fromJson[TypeDeclaration](Json.parse(jsonText)).asOpt.get
        es.description shouldBe None
        es.pattern shouldBe None
        es.examples shouldBe List()
        es.enumValues shouldBe List()
      }
    }
    "Response" should {
      "handle writing empty lists" in {
        val resp             = Response("123", List(), List(), None)
        val jsonText: String = Json.prettyPrint(Json.toJson(resp))
        jsonText.contains(""""body" : """) shouldBe false
        jsonText.contains(""""headers" : """) shouldBe false
      }

      "handle reading missing lists/values" in {
        val jsonText           =
          """{
            | "code": "123"
            |}""".stripMargin
        val response: Response = Json.fromJson[Response](Json.parse(jsonText)).asOpt.get
        response.description shouldBe None
        response.headers shouldBe List()
        response.body shouldBe List()
      }
    }
  }
}
