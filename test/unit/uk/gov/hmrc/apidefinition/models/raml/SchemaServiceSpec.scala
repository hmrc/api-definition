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

package unit.uk.gov.hmrc.apidefinition.models.raml

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import uk.gov.hmrc.apidefinition.models.apispecification.{EnumerationValue, JsonSchema}

import scala.io.Source

class SchemaServiceSpec extends WordSpec with Matchers {
  import SchemaTestHelper._

  def loader = TestSchemaService
  import uk.gov.hmrc.apidefinition.models.apispecification.ApiSpecificationFormatters._

  "The SchemaLoader" should {

    // TODO: Make these tests for with schema (RAML?) passed in

    // "load a schema that has no refs" in {
    //   // val expectedPlainText = loader.fetchPlainTextSchema("test/resources/schemaloader/input/schemas/schemas/norefs-schema.json")
    //   // val expectedSchema = loader.parseSchema("test/resources/schemaloader/expected/expected-norefs-schema.json")

    //   val jsonSchema: JsonSchema = TestSchemaService.parseSchema("schemaloader/input/schemas/norefs-schema.json")
    //   val jsonSchemaText = Json.prettyPrint(Json.toJson(jsonSchema))

    //   val expectedSchemaText = Json.prettyPrint(Json.parse(loadResourceTextFile("schemaloader/expected/expected-norefs-schema.json")))

    //   jsonSchemaText shouldBe expectedSchemaText
    // }

    // "load a schema that has internal refs" in {
    //   val raml = loadRaml("test/resources/schemaloader/input/internalrefs.raml")
    //   val expectedPlainText = loadPlainText("test/resources/schemaloader/input/schemas/internalrefs-schema.json")
    //   val expectedSchema = loadSchema("test/resources/schemaloader/expected/expected-internalrefs-schema.json")

    //   val actual = loader.loadSchemas("test/resources/schemaloader/input/schemas", raml)

    //   actual should have size 1
    //   actual shouldBe Map(expectedPlainText -> expectedSchema)
    // }

    // "load a schema that has nested internal refs" in {
    //   val raml = loadRaml("test/resources/schemaloader/input/nestedinternalrefs.raml")
    //   val expectedPlainText = loadPlainText("test/resources/schemaloader/input/schemas/nestedinternalrefs-schema.json")
    //   val expectedSchema = loadSchema("test/resources/schemaloader/expected/expected-nestedinternalrefs-schema.json")

    //   val actual = loader.loadSchemas("test/resources/schemaloader/input/schemas", raml)

    //   actual should have size 1
    //   actual shouldBe Map(expectedPlainText -> expectedSchema)
    // }

    // "load a schema that has external refs" in {
    //   val raml = loadRaml("test/resources/schemaloader/input/externalrefs.raml")
    //   val expectedPlainText = loadPlainText("test/resources/schemaloader/input/schemas/externalrefs-schema.json")
    //   val expectedSchema = loadSchema("test/resources/schemaloader/expected/expected-externalrefs-schema.json")

    //   val actual = loader.loadSchemas("test/resources/schemaloader/input/schemas", raml)

    //   actual should have size 1
    //   actual shouldBe Map(expectedPlainText -> expectedSchema)
    // }

    // "load a schema that has a chain of external refs" in {
    //   val raml = loadRaml("test/resources/schemaloader/input/complexrefs.raml")
    //   val expectedPlainText = loadPlainText("test/resources/schemaloader/input/complexrefs.raml")
    //   val expectedSchema = loadSchema("test/resources/schemaloader/expected/expected-complexrefs-schema.json")

    //   val actual = loader.loadSchemas("test/resources/schemaloader/input/schemas", raml)

    //   actual should have size 1
    //   actual shouldBe Map(expectedPlainText -> expectedSchema)
    // }
  }

  "parse json to JsonSchema" in {
    val jsonSchema = loader.parseSchema("schemas/reference-schema.json")

    jsonSchema.definitions.size shouldBe 1
    jsonSchema.definitions.head._1 shouldBe "my-id"
    // jsonSchema.definitions.head._2.id shouldBe Some("")
    jsonSchema.definitions.head._2.`type` shouldBe Some("string")
    jsonSchema.definitions.head._2.example shouldBe Some("my-example")
    jsonSchema.definitions.head._2.description shouldBe Some("my-description")
  }

  "convert JsonSchema to text when" when {
    "empty" in {
      val jsonSchema = JsonSchema()
      val jsonSchemaText = loader.toJsonString(jsonSchema)

      jsonSchemaText shouldBe """|{
                                 |  "properties" : { },
                                 |  "patternProperties" : { },
                                 |  "required" : [ ],
                                 |  "definitions" : { },
                                 |  "enum" : [ ],
                                 |  "oneOf" : [ ]
                                 |}""".stripMargin.replace("\n","").replace(" ","")
    }
    "populated" in {
      val jsonSchema = JsonSchema(
        description = Some("my-description"),
        id = Some("my-id"),
        `type` = Some("my-type"),
        example = Some("my-example"),
        title = Some("my-title"),
        // properties = ListMap(),
        //        patternProperties =
        //        items =
        //required =
        //definitions =
        //ref =
        `enum` = Seq(EnumerationValue("my-enum-a")),
        //        oneOf =
        //pattern =
      )

      val jsonSchemaText = Json.parse(loader.toJsonString(jsonSchema))

      jsonSchemaText shouldBe Json.parse( """|{
                                 |  "description" : "my-description",
                                 |  "id" : "my-id",
                                 |  "type" : "my-type",
                                 |  "example" : "my-example",
                                 |  "title" : "my-title",
                                 |  "properties" : { },
                                 |  "patternProperties" : { },
                                 |  "required" : [ ],
                                 |  "definitions" : { },
                                 |  "enum" : [ "my-enum-a" ],
                                 |  "oneOf" : [ ]
                                 |}""".stripMargin)
    }
  }

  "Parse enums in schema" in {
    val jsonSchema = TestSchemaService.parseSchema("schemas/schema-with-enums.json" )

    jsonSchema.description shouldBe Some("my enums field")

    jsonSchema.oneOf.size shouldBe 1
    jsonSchema.oneOf(0).`enum`.size shouldBe 2

    jsonSchema.oneOf(0).`enum`(0).value shouldBe "enum-a"
    jsonSchema.oneOf(0).`enum`(1).value shouldBe "enum-b"
  }

  private def loadResourceTextFile(uri: String): String = {
    Source.fromFile(s"test/resources/${uri}").mkString
  }
}
