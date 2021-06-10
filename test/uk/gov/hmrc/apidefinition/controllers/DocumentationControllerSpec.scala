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

package uk.gov.hmrc.apidefinition.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import uk.gov.hmrc.apidefinition.services.DocumentationService
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import play.api.test.Helpers._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Results._
import play.api.http.HeaderNames
import akka.util.ByteString

class DocumentationControllerSpec extends AsyncHmrcSpec with StubControllerComponentsFactory {

  trait Setup {
    // implicit val mat: Materializer = materializer
    val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val documentationService: DocumentationService = mock[DocumentationService]
    val hc: HeaderCarrier = HeaderCarrier()
    val serviceName: String = "api-example-microservice"
    val version: String = "1.0"
    val resourceName: String = "application.raml"
    // scalastyle:off magic.number
    // val body: Array[Byte] = Array[Byte](0x1, 0x2, 0x3)
    val body = "blah blah"
    // scalastyle:on magic.number
    val contentType: String = "application/text"

    val underTest = new DocumentationController(documentationService, stubControllerComponents())

    def theDocumentationServiceWillReturnTheResource = {
      when(documentationService.fetchApiDocumentationResource(*, *, *))
        .thenReturn(successful(Ok(body).withHeaders(CONTENT_TYPE -> contentType)))
    }

    def theDocumentationServiceWillFailToReturnTheResource = {
      when(documentationService.fetchApiDocumentationResource(*, *, *))
        .thenReturn(failed(new RuntimeException("Some message")))
    }

    def theDocumentationServiceWillReturnNotFound = {
      when(documentationService.fetchApiDocumentationResource(*, *, *))
        .thenReturn(failed(new NotFoundException("some message")))
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

      verify(documentationService).fetchApiDocumentationResource(eqTo(serviceName), eqTo(version), eqTo(resourceName))
    }

    "return the resource with a Content-type header when the content type is known" in new Setup {
      theDocumentationServiceWillReturnTheResource

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe OK

      contentAsString(result) shouldBe body

      header(HeaderNames.CONTENT_TYPE, result) shouldBe Some(contentType)
    }

    "return the resource with no Content-type header when the content type is unknown" in new Setup {
      theDocumentationServiceWillReturnTheResource

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe OK

      contentAsString(result) shouldBe body
    }

    "return internal server error when the service fails to return the resource" in new Setup {
      theDocumentationServiceWillFailToReturnTheResource

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return not found when the service is unable to find the api or version" in new Setup {
      theDocumentationServiceWillReturnNotFound

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe NOT_FOUND
    }
  }

  def sourceToArray(dataStream: Source[ByteString, _])(implicit mat: Materializer): Array[Byte] = {
    await(dataStream
      .runWith(Sink.reduce[ByteString](_ ++ _))
      .map { r: ByteString => r.toArray[Byte] })
  }
}
