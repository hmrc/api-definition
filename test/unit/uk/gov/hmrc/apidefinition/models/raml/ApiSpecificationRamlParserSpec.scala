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

import uk.gov.hmrc.play.test.UnitSpec
import RamlSpecHelper.loadRaml
import uk.gov.hmrc.apidefinition.models.apispecification.SecurityScheme
import uk.gov.hmrc.apidefinition.models.apispecification.DocumentationItem
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParser
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import uk.gov.hmrc.apidefinition.models.apispecification.JsonSchema

class ApiSpecificationRamlParserSpec extends UnitSpec {
  "RAML to apiSpec" should {
    "Simple.raml should parse title and version to our model" in {
      val raml = loadRaml("V2/simple.raml")

      val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
      apiSpec.title shouldBe "My simple title"
      apiSpec.version shouldBe "My version"

      apiSpec.documentationItems.size shouldBe 2
      apiSpec.documentationItems(0) shouldBe DocumentationItem("Overview", "Some overview")
      apiSpec.documentationItems(1) shouldBe DocumentationItem("Versioning", "Some versioning")
    }

    "With single method" in {
      val raml = loadRaml("V2/single-method.raml")

      val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
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

      val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
      apiSpec.resourceGroups.size shouldBe 1

      val rg = apiSpec.resourceGroups(0)

      rg.resources(0).displayName shouldBe "/endpoint1"
      rg.resources(1).displayName shouldBe "/endpoint2"
      rg.resources(2).displayName shouldBe "/endpoint3"
    }

    "With multiple methods maintain RAML ordering" in {
      val raml = loadRaml("V2/multiple-methods.raml")

      val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)

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

      val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)

      val rg = apiSpec.resourceGroups(0)

      val m0 = rg.resources(0).methods(0)
      val m1 = rg.resources(1).methods(0)
      val m2 = rg.resources(2).methods(0)

      m0.securedBy shouldBe None

      m1.securedBy shouldBe Some(SecurityScheme("user", Some("all:test-me")))
      m2.securedBy shouldBe Some(SecurityScheme("application", None))
    }


    "With global type with enums" in {
      val raml = loadRaml("V2/typed-enums.raml")

      val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
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

        val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
        apiSpec.resourceGroups.size shouldBe 1

        val body = apiSpec.resourceGroups(0).resources(0).methods(0).body(0)

        val jsonSchema = Json.parse(body.`type`).as[JsonSchema]

        jsonSchema.description shouldBe Some("Schema details")
      }

      "Load basic !include json schema" in {
        val raml = loadRaml("V2/with-included-json-schema.raml")

        val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
        apiSpec.resourceGroups.size shouldBe 1

        val body = apiSpec.resourceGroups(0).resources(0).methods(0).body(0)

        val json: JsValue = Json.parse(body.`type`)
        val jsonSchema = json.validate[JsonSchema].asOpt.get

        jsonSchema.description shouldBe Some("External schema details")
      }

      // TODO: WIP
      "Load basic !include json schema with reference" ignore {
        val raml = loadRaml("V2/with-json-schema-with-references.raml")

        val apiSpec = ApiSpecificationRamlParser.toApiSpecification(raml)
        apiSpec.resourceGroups.size shouldBe 1

        val body = apiSpec.resourceGroups(0).resources(0).methods(0).body(0)

        val json: JsValue = Json.parse(body.`type`)
        val jsonSchema = json.validate[JsonSchema].asOpt.get

        jsonSchema.description shouldBe Some("reference schema details")

        val properties = jsonSchema.properties("my-id")
        
        properties.description shouldBe Some("my-description")
        properties.example shouldBe Some("my-example")
      }
    }
  }
}
