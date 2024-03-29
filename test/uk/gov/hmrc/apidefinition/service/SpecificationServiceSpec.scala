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

package uk.gov.hmrc.apidefinition.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.ramltools.domain.RamlNotFoundException
import uk.gov.hmrc.ramltools.loaders.RamlLoader

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.apispecification.RamlSpecHelper
import uk.gov.hmrc.apidefinition.raml.{ApiSpecificationRamlParser, RAML}
import uk.gov.hmrc.apidefinition.services.{SchemaService, SpecificationService}
import uk.gov.hmrc.apidefinition.utils.{AsyncHmrcSpec, Utils}

class SpecificationServiceSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Utils {

  implicit val materializer: Materializer = app.materializer

  val raml: Try[RAML.RAML] = Success(RamlSpecHelper.loadRaml("V2/simple.raml"))

  trait Setup {
    val ramlLoader = mock[RamlLoader]

    val config: AppConfig = mock[AppConfig]
    when(config.serviceBaseUrl).thenReturn("")

    val parser: ApiSpecificationRamlParser = new ApiSpecificationRamlParser(new SchemaService)

    val specificationService: SpecificationService = new SpecificationService(config, ramlLoader, parser)
  }

  "SpecificationService" should {
    "fetch and parse raml" in new Setup {
      when(ramlLoader.load(any[String])).thenReturn(raml)

      val ojs = await(specificationService.fetchApiSpecification(ServiceName("api-not-real"), ApiVersionNbr("1.0")))

      Json.stringify(ojs.value).contains(""""title":"My simple title"""") shouldBe true
    }

    "fetch and handle no raml found" in new Setup {
      when(ramlLoader.load(any[String])).thenReturn(Failure(RamlNotFoundException("")))

      val ojs = await(specificationService.fetchApiSpecification(ServiceName("api-not-real"), ApiVersionNbr("1.0")))

      ojs shouldBe None
    }
  }
}
