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
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Result, Results}
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import uk.gov.hmrc.apidefinition.controllers.DocumentationController
import uk.gov.hmrc.apidefinition.services.DocumentationService
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.apidefinition.controllers.SpecificationController

class DocumentationControllerSpec extends UnitSpec with ScalaFutures with MockitoSugar with WithFakeApplication with StubControllerComponentsFactory {

  trait Setup {
    implicit val mat: Materializer = fakeApplication.materializer
    val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val documentationService: DocumentationService = mock[DocumentationService]
    val hc: HeaderCarrier = HeaderCarrier()
    val serviceName: String = "api-example-microservice"
    val version: String = "1.0"
    val resourceName: String = "application.raml"
    // scalastyle:off magic.number
    val body: Array[Byte] = Array[Byte](0x1, 0x2, 0x3)
    // scalastyle:on magic.number
    val contentType: String = "application/text"

    val underTest = new DocumentationController(documentationService, stubControllerComponents())

    def theDocumentationServiceWillReturnTheResource: OngoingStubbing[Future[Result]] = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(Results.Ok(body).withHeaders(CONTENT_TYPE -> contentType)))
    }

    def theDocumentationServiceWillFailToReturnTheResource: OngoingStubbing[Future[Result]] = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("Some message")))
    }

    def theDocumentationServiceWillReturnNotFound: OngoingStubbing[Future[Result]] = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(new NotFoundException("some message")))
    }
  }

  trait RegistrationSetup extends Setup {
    val versions = Seq("1.0", "1.1", "2.0")
    val versionsJsonString: String = versions.map(v => s""""$v"""").mkString(",")
    val url = "https://abc.example.com"
    val registrationRequestBody: String =
      s"""{
         |  "serviceName": "$serviceName",
         |  "serviceUrl": "$url",
         |  "serviceVersions": [$versionsJsonString]
         }""".stripMargin
    val registrationRequest: FakeRequest[JsValue] = FakeRequest().withBody(Json.parse(registrationRequestBody))

  }

  "fetchApiDocumentationResource" should {

    "call the service to get the resource" in new Setup {
      theDocumentationServiceWillReturnTheResource

      await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      verify(documentationService).fetchApiDocumentationResource(eqTo(serviceName), eqTo(version), eqTo(resourceName))(any[HeaderCarrier])
    }

    "return the resource with a Content-type header when the content type is known" in new Setup {
      theDocumentationServiceWillReturnTheResource

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe OK

      val resultData: Array[Byte] = sourceToArray(result.body.dataStream)
      resultData.toList shouldBe body.toList

      result.header.headers(CONTENT_TYPE) shouldBe contentType
    }

    "return the resource with no Content-type header when the content type is unknown" in new Setup {
      theDocumentationServiceWillReturnTheResource

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe OK

      sourceToArray(result.body.dataStream).toList shouldBe body
    }

    "return internal server error when the service fails to return the resource" in new Setup {
      theDocumentationServiceWillFailToReturnTheResource

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return not found when the service is unable to find the api or version" in new Setup {
      theDocumentationServiceWillReturnNotFound

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe NOT_FOUND
    }
  }

  def sourceToArray(dataStream: Source[ByteString, _])(implicit mat: Materializer): Array[Byte] = {
    await(dataStream
      .runWith(Sink.reduce[ByteString](_ ++ _))
      .map { r: ByteString => r.toArray[Byte] })
  }
}
