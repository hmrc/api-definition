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

import RamlSpecHelper.loadRaml
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParser
import uk.gov.hmrc.apidefinition.services.SchemaService

class GroupedResourcesSpec extends AsyncHmrcSpec
 {
  val schemaService = new SchemaService()
  val apiSpecificationRamlParser = new ApiSpecificationRamlParser(schemaService)

  val basePath = "test/resources/raml/V2"

  "Load grouped resources" in {
    val raml = loadRaml("V2/grouped-methods-1.raml")

    val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
    apiSpec.resourceGroups.size shouldBe 3

    val groups = Table(
      ("id",  "name",           "description",              "resource count"),
      (0,     None,             None,                       List("/endpoint1")),
      (1,     Some("group 1"),  Some("Manage something 1"), List("/endpoint2", "/endpoint3")),
      (2,     Some("group 2"),  Some("Manage something 2"), List("/endpoint4"))
    )

    forAll(groups) { (id, expectedName, expectedDescription, endpoints ) => 
      val g = apiSpec.resourceGroups(id)
      g.name shouldBe expectedName
      g.description shouldBe expectedDescription
      g.resources.size shouldBe endpoints.size
      g.resources.map(r=>r.resourcePath) shouldBe endpoints
    }
  }

  "Load grouped resources with nested group annotations" in {
    val raml = loadRaml("V2/grouped-methods-2.raml")

    val apiSpec = apiSpecificationRamlParser.toApiSpecification(basePath, raml)
    apiSpec.resourceGroups.size shouldBe 2

    val groups = Table(
      ("id",  "resource count"),
      (0,     List("/endpoint1")),
      (1,     List("/endpoint1/sub1", "/endpoint1/sub2")),
    )

    forAll(groups) { (id, endpoints ) => 
      val g = apiSpec.resourceGroups(id)
      g.resources.map(r=>r.resourcePath) shouldBe endpoints
    }
  }
}
