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

package uk.gov.hmrc.apidefinition.models.raml

import scala.io.Source

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import play.api.libs.json.{JsValue, Json}

import uk.gov.hmrc.apidefinition.models.apispecification.{EnumerationValue, JsonSchema}

class SchemaServiceSpec extends AnyWordSpec with Matchers {

  import SchemaTestHelper._

  def loader = TestSchemaService

  private def expectation(fileName: String): JsValue = {
    Json.parse(loadResourceTextFile(s"schemaloader/expected/expected-${fileName}-schema.json"))
  }

  private def actualSchema(fileName: String): JsonSchema = {
    TestSchemaService.parseSchemaInFolder(s"${fileName}-schema.json", "schemaloader/input/schemas")
  }

  def compareJson(expected: JsValue, actual: JsonSchema): Assertion = {
    val actualText   = Json.prettyPrint(Json.toJson(actual))
    val expectedText = Json.prettyPrint(expected)

    actualText shouldBe expectedText
  }

  def compareActualVsExpectedJson(fileName: String): Assertion = {
    compareJson(expectation(fileName), actualSchema(fileName))
  }

  "The SchemaLoader" should {

    "load a schema that has no refs" in {
      compareActualVsExpectedJson("norefs")
    }

    "load a schema that has internal refs" in {
      compareActualVsExpectedJson("internalrefs")
    }

    "load a schema that has nested internal refs" in {
      compareActualVsExpectedJson("nestedinternalrefs")
    }

    "load a schema that has external refs" in {
      compareActualVsExpectedJson("externalrefs")
    }

    "load a schema that has a chain of external refs" in {
      compareActualVsExpectedJson("complexrefs")
    }
  }

  "parse json to JsonSchema" in {
    val jsonSchema = loader.parseSchema("schemas/reference-schema.json")

    jsonSchema.definitions.size shouldBe 1
    jsonSchema.definitions.head._1 shouldBe "my-id"
    jsonSchema.definitions.head._2.`type` shouldBe Some("string")
    jsonSchema.definitions.head._2.example shouldBe Some("my-example")
    jsonSchema.definitions.head._2.description shouldBe Some("my-description")
  }

  "convert JsonSchema to text when" when {
    "empty" in {
      val jsonSchema     = JsonSchema()
      val jsonSchemaText = loader.toJsonString(jsonSchema)

      jsonSchemaText shouldBe "{}"
    }
    "populated" in {
      val jsonSchema = JsonSchema(
        description = Some("my-description"),
        id = Some("my-id"),
        `type` = Some("my-type"),
        example = Some("my-example"),
        title = Some("my-title"),
        enumValue = Seq(EnumerationValue("my-enum-a"))
      )

      val jsonSchemaText = Json.parse(loader.toJsonString(jsonSchema))

      jsonSchemaText shouldBe Json.parse(
        """|{
           |  "description" : "my-description",
           |  "id" : "my-id",
           |  "type" : "my-type",
           |  "example" : "my-example",
           |  "title" : "my-title",
           |  "enum" : [ "my-enum-a" ]
           |}""".stripMargin
      )
    }
  }

  "Parse enums in schema" in {
    val jsonSchema = TestSchemaService.parseSchema("schemas/schema-with-enums.json")

    jsonSchema.description shouldBe Some("my enums field")

    jsonSchema.oneOf.size shouldBe 1
    jsonSchema.oneOf.head.enumValue.size shouldBe 2

    jsonSchema.oneOf.head.enumValue.head.value shouldBe "enum-a"
    jsonSchema.oneOf.head.enumValue(1).value shouldBe "enum-b"
  }

  private def loadResourceTextFile(uri: String): String = {
    Source.fromFile(s"test/resources/${uri}").mkString
  }
}
