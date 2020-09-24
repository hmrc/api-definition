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

package unit.uk.gov.hmrc.apidefinition.controllers

import akka.stream.Materializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.test.{FakeRequest, StubControllerComponentsFactory, StubPlayBodyParsersFactory}
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito.{verify, verifyZeroInteractions, when}
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apidefinition.controllers.SpecificationController
import uk.gov.hmrc.apidefinition.services.SpecificationService
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Result
import play.api.http.Status._
import play.api.libs.json.Json
import scala.concurrent.Future

class SpecificationControllerSpec extends UnitSpec
  with WithFakeApplication with ScalaFutures with MockitoSugar with StubControllerComponentsFactory with StubPlayBodyParsersFactory {

  trait Setup {
    implicit lazy val materializer: Materializer = fakeApplication.materializer

    implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    val mockSpecificationService: SpecificationService = mock[SpecificationService]
    val mockAppConfig: AppConfig = mock[AppConfig]
      
    val underTest = new SpecificationController(mockSpecificationService, mockAppConfig, stubControllerComponents())
  }

  "fetchApiSpecification action should return json specification" in new Setup {
    val serviceName = "my-service-name"
    val version = "1.0"
    val specificationJson = Json.toJson("some" -> "stuff")

    when(mockSpecificationService.fetchApiSpecification(any(), any())).thenReturn(Future.successful(specificationJson))

    val result : Result = await(underTest.fetchApiSpecification(serviceName, version)(request))

    result.header.status should be(OK)

    jsonBodyOf(result) shouldEqual Json.toJson("some" -> "stuff")

    verify(mockSpecificationService).fetchApiSpecification(eqTo(serviceName), eqTo(version))
  }

  "fetchPreviewApiSpecification action should return json specification" in new Setup {
    val specificationJson = Json.toJson("some" -> "stuff")
    val rootRamlUrl = "http://localhost:8080/fake-url"

    when(mockSpecificationService.fetchPreviewApiSpecification(any())).thenReturn(Future.successful(specificationJson))

    val result : Result = await(underTest.fetchPreviewApiSpecification(rootRamlUrl)(request))

    result.header.status should be(OK)

    jsonBodyOf(result) shouldEqual Json.toJson("some" -> "stuff")

    verify(mockSpecificationService).fetchPreviewApiSpecification(eqTo(rootRamlUrl))
  }

}
