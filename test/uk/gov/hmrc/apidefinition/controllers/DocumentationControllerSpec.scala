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

import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{FakeRequest, StubControllerComponentsFactory}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import uk.gov.hmrc.apidefinition.mocks.DocumentationServiceMockModule
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class DocumentationControllerSpec extends AsyncHmrcSpec with StubControllerComponentsFactory {

  trait Setup extends DocumentationServiceMockModule {
    val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val hc: HeaderCarrier                            = HeaderCarrier()
    val serviceName: ServiceName                     = ServiceName("api-example-microservice")
    val version: ApiVersionNbr                       = ApiVersionNbr("1.0")
    val resourceName: String                         = "application.raml"
    val body                                         = "blah blah"
    val contentType: String                          = "application/text"

    val underTest = new DocumentationController(DocumentationServiceMock.documentationMock, stubControllerComponents())

  }

  trait RegistrationSetup extends Setup {
    val versions                   = Seq("1.0", "1.1", "2.0")
    val versionsJsonString: String = versions.map(v => s""""$v"""").mkString(",")
    val url                        = "https://abc.example.com"

    val registrationRequestBody: String           =
      s"""{
         |  "serviceName": "$serviceName",
         |  "serviceUrl": "$url",
         |  "serviceVersions": [$versionsJsonString]
         }""".stripMargin
    val registrationRequest: FakeRequest[JsValue] = FakeRequest().withBody(Json.parse(registrationRequestBody))

  }

  "fetchApiDocumentationResource" should {

    "call the service to get the resource" in new Setup {
      DocumentationServiceMock.FetchApiDocumentationResource.success(body)

      await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      DocumentationServiceMock.FetchApiDocumentationResource.verifyCalled(serviceName, version, resourceName)
    }

    "return the resource with a Content-type header when the content type is known" in new Setup {
      DocumentationServiceMock.FetchApiDocumentationResource.success(body, contentType)

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe OK

      contentAsString(result) shouldBe body

      header(HeaderNames.CONTENT_TYPE, result) shouldBe Some(contentType)
    }

    "return the resource with no Content-type header when the content type is unknown" in new Setup {
      DocumentationServiceMock.FetchApiDocumentationResource.success(body)

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe OK

      contentAsString(result) shouldBe body
    }

    "return internal server error when the service fails to return the resource" in new Setup {
      DocumentationServiceMock.FetchApiDocumentationResource.errors(new RuntimeException("Some message"))

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return not found when the service is unable to find the api or version" in new Setup {
      DocumentationServiceMock.FetchApiDocumentationResource.errors(new NotFoundException("some message"))

      private val result = underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request)

      status(result) shouldBe NOT_FOUND
    }
  }

}
