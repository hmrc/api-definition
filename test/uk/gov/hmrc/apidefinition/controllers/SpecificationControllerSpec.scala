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

package uk.gov.hmrc.apidefinition.controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{FakeRequest, StubControllerComponentsFactory, StubPlayBodyParsersFactory}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.services.SpecificationService
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class SpecificationControllerSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with StubControllerComponentsFactory with StubPlayBodyParsersFactory {

  trait Setup {
    implicit lazy val materializer: Materializer = app.materializer

    implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val mockSpecificationService: SpecificationService = mock[SpecificationService]
    val mockAppConfig: AppConfig                       = mock[AppConfig]

    val underTest = new SpecificationController(mockSpecificationService, mockAppConfig, stubControllerComponents())
  }

  "fetchApiSpecification action should return json specification" in new Setup {
    val serviceName       = ServiceName("my-service-name")
    val version           = ApiVersionNbr("1.0")
    val specificationJson = Json.toJson("some" -> "stuff")

    when(mockSpecificationService.fetchApiSpecification(*[ServiceName], *[ApiVersionNbr])).thenReturn(successful(Some(specificationJson)))

    private val result = underTest.fetchApiSpecification(serviceName, version)(request)

    status(result) should be(OK)

    contentAsJson(result) shouldEqual Json.toJson("some" -> "stuff")

    verify(mockSpecificationService).fetchApiSpecification(eqTo(serviceName), eqTo(version))
  }

  "fetchPreviewApiSpecification action should return json specification" in new Setup {
    val specificationJson = Json.toJson("some" -> "stuff")
    val rootRamlUrl       = "http://localhost:8080/fake-url"

    when(mockSpecificationService.fetchPreviewApiSpecification(*)).thenReturn(successful(Some(specificationJson)))

    private val result = underTest.fetchPreviewApiSpecification(rootRamlUrl)(request)

    status(result) should be(OK)

    contentAsJson(result) shouldEqual Json.toJson("some" -> "stuff")

    verify(mockSpecificationService).fetchPreviewApiSpecification(eqTo(rootRamlUrl))
  }

  "fetchApiSpecification action should return not found when no specification returned" in new Setup {
    val serviceName = ServiceName("my-service-name")
    val version     = ApiVersionNbr("1.0")

    when(mockSpecificationService.fetchApiSpecification(*[ServiceName], *[ApiVersionNbr])).thenReturn(successful(None))

    private val result = underTest.fetchApiSpecification(serviceName, version)(request)

    status(result) should be(NOT_FOUND)

    contentAsString(result) shouldEqual "RAML not found in this environment"

    verify(mockSpecificationService).fetchApiSpecification(eqTo(serviceName), eqTo(version))
  }
}
