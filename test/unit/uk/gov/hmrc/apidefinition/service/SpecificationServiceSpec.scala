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

package unit.uk.gov.hmrc.apidefinition.service

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.play.test.UnitSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import unit.uk.gov.hmrc.apidefinition.utils.Utils
import uk.gov.hmrc.apidefinition.services.SpecificationService
import scala.concurrent.ExecutionContext.Implicits.global
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.{any}
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParser
import uk.gov.hmrc.apidefinition.raml.RAML
import unit.uk.gov.hmrc.apidefinition.models.apispecification.RamlSpecHelper
import uk.gov.hmrc.ramltools.loaders.RamlLoader
import scala.util.Success
import scala.util.Try
import uk.gov.hmrc.apidefinition.services.SchemaService
import play.api.libs.json.Json


class SpecificationServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar with Utils {

  val raml: Try[RAML.RAML] = Success(RamlSpecHelper.loadRaml("V2/simple.raml"))

  val ramlLoader = mock[RamlLoader]
  when(ramlLoader.load(any[String])).thenReturn(raml)

  val config: AppConfig = mock[AppConfig]
  when(config.serviceBaseUrl).thenReturn("")

  val expected = Json.parse("""{"title":"My simple title","version":"My version","documentationItems":[{"title":"Overview","content":"Some overview"},{"title":"Versioning","content":"Some versioning"}],"resourceGroups":[],"types":[],"isFieldOptionalityKnown":true}""")

  val parser: ApiSpecificationRamlParser = new ApiSpecificationRamlParser(new SchemaService)

  "SpecificationService" should {
    "fetch and parse raml" in {

      val specificationService: SpecificationService = new SpecificationService(config, ramlLoader, parser)

      val js = await(specificationService.fetchSpecification("api-not-real", "1.0"))

      Json.stringify(js).contains(""""title":"My simple title"""") shouldBe true
    }
  }
}
