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

package unit.uk.gov.hmrc.apidefinition.models

import uk.gov.hmrc.play.test.UnitSpec
import scala.util.{Failure, Success}
import uk.gov.hmrc.ramltools.loaders.ComprehensiveClasspathRamlLoader
import uk.gov.hmrc.apidefinition.models.wiremodel.ApiSpecification
import uk.gov.hmrc.apidefinition.models.wiremodel.RAML
import uk.gov.hmrc.apidefinition.models.wiremodel.RAML.RAML

class ApiSpecificationSpec extends UnitSpec {
  "RAML to apiSpec" should {
    "Simple.raml should parse title and version to our model" in {
      val raml = loadRaml("V2/simple.raml")

      val apiSpec = ApiSpecification(raml)
      apiSpec.title shouldBe "My simple title"
      apiSpec.version shouldBe "My version"
    }

    "With single method" in {
      val raml = loadRaml("V2/single-method.raml")

      val apiSpec = ApiSpecification(raml)
      apiSpec.resourceGroups.size shouldBe 1

      val rg = apiSpec.resourceGroups(0)
      rg.description shouldBe None
      rg.name shouldBe None

      val r = rg.resources(0)

      r.resourcePath shouldBe "/my/endpoint"
      r.methods.length shouldBe 1

      val m = r.methods(0)
      m.displayName shouldBe "My endpoint"
      m.description shouldBe Some("My description")

      // TODO: Check endpoint URL, description
      // TODO: Doesn't handle missing description (null pointer)
    }

    "With multiple endpoints maintain RAML ordering" in {
      val raml = loadRaml("V2/multiple-methods.raml")

      val apiSpec = ApiSpecification(raml)
      apiSpec.resourceGroups.size shouldBe 1

      val rg = apiSpec.resourceGroups(0)

      // println("**** Actual Method Ordering: " + rg.resources.map(r=>r.displayName).mkString(","))

      rg.resources(0).displayName shouldBe "/endpoint1"
      rg.resources(1).displayName shouldBe "/endpoint2"
      rg.resources(2).displayName shouldBe "/endpoint3"
    }

    "With global type with enums" in {
      val raml = loadRaml("V2/typed-enums.raml")

      val apiSpec = ApiSpecification(raml)
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
  }

  def loadRaml(filename: String) : RAML = {
    new ComprehensiveClasspathRamlLoader().load(s"test/resources/raml/$filename") match {
      case Failure(exception) => throw exception
      case Success(raml) => raml
    }
  }
}
