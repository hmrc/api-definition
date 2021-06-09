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

package uk.gov.hmrc.apidefinition.models.apispecification

import uk.gov.hmrc.play.test.UnitSpec
import RamlSpecHelper.loadRaml
import uk.gov.hmrc.apidefinition.models.apispecification.SecurityScheme
import uk.gov.hmrc.apidefinition.models.apispecification.DocumentationItem
import play.api.libs.json.Json

class ApiSpecificationRamlParserSpec extends UnitSpec {
  import uk.gov.hmrc.apidefinition.models.raml.SchemaTestHelper._

  "RAML to apiSpec" should {
    "Simple.raml should parse title and version to our model" in {
      val raml = loadRaml("V2/simple.raml")

      val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
      apiSpec.title shouldBe "My simple title"
      apiSpec.version shouldBe "My version"

      apiSpec.documentationItems.size shouldBe 2
      apiSpec.documentationItems(0) shouldBe DocumentationItem("Overview", "Some overview")
      apiSpec.documentationItems(1) shouldBe DocumentationItem("Versioning", "Some versioning")
    }

    "With single method" in {
      val raml = loadRaml("V2/single-method.raml")

      val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
      apiSpec.resourceGroups.size shouldBe 1

      val rg = apiSpec.resourceGroups(0)
      rg.description shouldBe None
      rg.name shouldBe None

      val resource = rg.resources(0)

      resource.resourcePath shouldBe "/my/endpoint"
      resource.methods.length shouldBe 1

      val m = resource.methods(0)
      m.displayName shouldBe "My endpoint"
      m.description shouldBe Some("My description")

      m.headers(0).name shouldBe "Accept"
      m.headers(0).`type` shouldBe "string"
      m.headers(0).required shouldBe true

      val response = m.responses(0)
      response.code shouldBe "200"
      response.description shouldBe Some("When it works")
      response.body.size shouldBe 1
      response.body(0).name shouldBe "application/json"
      response.body(0).example.get.value shouldBe Some("""{ "message": "good" }"""+"\n")
    }

    "With multiple endpoints maintain RAML ordering" in {
      val raml = loadRaml("V2/multiple-endpoints.raml")

      val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
      apiSpec.resourceGroups.size shouldBe 1

      val rg = apiSpec.resourceGroups(0)

      rg.resources(0).displayName shouldBe "/endpoint1"
      rg.resources(1).displayName shouldBe "/endpoint2"
      rg.resources(2).displayName shouldBe "/endpoint3"
    }

    "With multiple methods maintain RAML ordering" in {
      val raml = loadRaml("V2/multiple-methods.raml")

      val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)

      val rg = apiSpec.resourceGroups(0)

      val r0 = rg.resources(0)
      val r1 = rg.resources(1)

      r0.displayName shouldBe "/endpoint1"
      r0.methods.size shouldBe 2
      r0.methods(0).method shouldBe "get"
      r0.methods(0).description shouldBe Some("1")
      r0.methods(1).method shouldBe "post"
      r0.methods(1).description shouldBe Some("1b")

      r1.displayName shouldBe "/endpoint2"
      r1.methods.size shouldBe 2
      r1.methods(0).method shouldBe "get"
      r1.methods(0).description shouldBe Some("2")
      r1.methods(1).method shouldBe "post"
      r1.methods(1).description shouldBe Some("2b")
    }

    "With security schemes in RAML" in {
      val raml = loadRaml("V2/multiple-security-options.raml")

      val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)

      val rg = apiSpec.resourceGroups(0)

      val m0 = rg.resources(0).methods(0)
      val m1 = rg.resources(1).methods(0)
      val m2 = rg.resources(2).methods(0)
      val m3 = rg.resources(3).methods(0)

      m0.securedBy shouldBe None

      m1.securedBy shouldBe Some(SecurityScheme("user", Some("all:test-me")))
      m2.securedBy shouldBe Some(SecurityScheme("application", None))
      m3.securedBy shouldBe Some(SecurityScheme("application", Some("all:test-me-too")))
    }


    "With global type with enums" in {
      val raml = loadRaml("V2/typed-enums.raml")

      val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
      apiSpec.resourceGroups.size shouldBe 1

      val rg = apiSpec.resourceGroups(0)

      rg.resources(0).displayName shouldBe "/my/endpoint"
      val qps = rg.resources(0).methods.head.queryParameters
      val qp1 = qps.head
      qp1.name shouldBe "aParam"
      qp1.enumValues shouldBe List("1","2")

      val qp2 = qps.tail.head

      qp2.name shouldBe "anotherParam"
      qp2.enumValues shouldBe List("a","b","c")
    }

    "schema" when {
      "Load basic inline json schema" in {
        val raml = loadRaml("V2/with-inline-json-schema.raml")

        val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
        apiSpec.resourceGroups.size shouldBe 1

        val body = apiSpec.resourceGroups(0).resources(0).methods(0).body(0)

        Json.parse(body.`type`) shouldBe Json.parse("""
        {
          "description":"Schema details",
          "type":"object",
          "properties":{
              "id":{
                "type":"integer"
              },
              "name":{
                "type":"string"
              },
              "ownerName":{
                "type":"string"
              }
          },
          "required":[ "id", "name" ]
        }"""
        )
      }

      "Load basic !include json schema" in {
        val raml = loadRaml("V2/with-included-json-schema.raml")

        val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
        apiSpec.resourceGroups.size shouldBe 1

        val body = apiSpec.resourceGroups(0).resources(0).methods(0).body(0)

        println(Json.prettyPrint(Json.parse(body.`type`)))

       Json.parse(body.`type`) shouldBe Json.parse("""{
          "description":"External schema details",
          "type":"object",
          "properties":{
              "id":{
                "type":"integer"
              },
              "name":{
                "type":"string"
              },
              "ownerName":{
                "type":"string"
              }
          },
          "required":["id","name"]
        }""")
      }

      "Load basic !include json schema with reference" in {
        val raml = loadRaml("V2/with-json-schema-with-references.raml")

        val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
        apiSpec.resourceGroups.size shouldBe 1

        val body = apiSpec.resourceGroups(0).resources(0).methods(0).body(0)

        Json.parse(body.`type`) shouldBe Json.parse("""
          {
            "description":"reference schema details",
            "type":"object",
            "properties":{
                "my-id":{
                  "description":"my-description",
                  "type":"string",
                  "example":"my-example"
                }
            },
            "required":["id","name"]
          }"""
        )
      }

      "Parsing error responses" in {
        val raml = loadRaml("V2/applicationWithErrorExample.raml")

        val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
        apiSpec.resourceGroups.size shouldBe 1

        val responses = apiSpec.resourceGroups(0).resources(0).methods(0).responses

        responses.filter(r => r.code == "400").map( err => {
          err.description shouldBe Some("The user is not authorized to access the given GMR")
          val example = err.body.head.examples(0)
          example.code shouldBe Some("")
          example.documentation shouldBe Some("unmatchedRecipient")
        })
      }
    }
  }
}
