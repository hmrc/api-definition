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

import java.util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._
import scala.util.Random

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseStatus, CacheableResponse}
import play.api.libs.ws.ahc.{AhcWSResponse, StandaloneAhcWSResponse}
import play.api.mvc.Result
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ApiMicroserviceConnector
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.{DocumentationService, SpecificationService}
import uk.gov.hmrc.apidefinition.utils.{AsyncHmrcSpec, Utils}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._

class DocumentationServiceSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Utils {
  import DocumentationService.PROXY_SAFE_CONTENT_TYPE

  val serviceName = "hello-world"
  val version     = "1.0"
  val serviceUrl  = "http://localhost"

  val productionV1Availability: ApiAvailability = ApiAvailability(
    endpointsEnabled = true,
    ApiAccess.Private(Nil),
    loggedIn = false,
    authorised = false
  )

  val productionV2Availability: ApiAvailability = ApiAvailability(
    endpointsEnabled = true,
    ApiAccess.Private(Nil),
    loggedIn = false,
    authorised = false
  )

  val sandboxV2Availability: ApiAvailability = ApiAvailability(
    endpointsEnabled = true,
    ApiAccess.PUBLIC,
    loggedIn = false,
    authorised = false
  )

  val sandboxV3Availability: ApiAvailability = ApiAvailability(
    endpointsEnabled = false,
    ApiAccess.PUBLIC,
    loggedIn = false,
    authorised = false
  )

  val apiDefinition: ApiDefinition = ApiDefinition(
    serviceName = serviceName,
    serviceBaseUrl = serviceUrl,
    name = "Hello World",
    description = "Example",
    context = ApiContext("hello"),
    requiresTrust = false,
    isTestSupport = false,
    versions = List(
      ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, endpoints = Nil, endpointsEnabled = false),
      ApiVersion(ApiVersionNbr("2.0"), ApiStatus.BETA, endpoints = Nil),
      ApiVersion(ApiVersionNbr("3.0"), ApiStatus.ALPHA, endpoints = Nil)
    ),
    lastPublishedAt = None,
    categories = List(ApiCategory.OTHER)
  )

  private val sampleFileSource: Source[ByteString, _] = createSourceFrom("hello")

  def createResponse(statusCode: Int, headers: Map[String, String], fileSource: Source[ByteString, _]): WSResponse = {
    val uri: Uri       = Uri.create(serviceUrl)
    val status         = new CacheableHttpResponseStatus(uri, statusCode, "", "")
    val defaultHeaders = new DefaultHttpHeaders()
    headers foreach { case (k, v) => defaultHeaders.add(k, v) }
    // val responseHeaders = CacheableHttpResponseHeaders(trailingHeaders = false, headers = defaultHeaders)
    val bodyParts      = util.Collections.emptyList[CacheableHttpResponseBodyPart]

    val cf: AsyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder().build()

    // TODO work out how to add source bytestream to bodyparts & copy headers
    AhcWSResponse(new StandaloneAhcWSResponse(CacheableResponse(status = status, headers = defaultHeaders, bodyParts = bodyParts, ahcConfig = cf)))
  }

  private val streamedResource: WSResponse            =
    createResponse(Status.OK, Map("Content-Type" -> "application/text", "Content-Length" -> "hello".length.toString), sampleFileSource)
  private val chunkedResource: WSResponse             = createResponse(Status.OK, Map.empty, sampleFileSource)
  private val notFoundResponse: WSResponse            = createResponse(Status.NOT_FOUND, Map.empty, sampleFileSource)
  private val internalServerErrorResponse: WSResponse = createResponse(Status.INTERNAL_SERVER_ERROR, Map.empty, sampleFileSource)

  implicit val materializer: Materializer = app.materializer

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockAPIDefinitionRepository: APIDefinitionRepository   = mock[APIDefinitionRepository]
    val mockApiMicroserviceConnector: ApiMicroserviceConnector = mock[ApiMicroserviceConnector]
    val mockServiceConfig: AppConfig                           = mock[AppConfig]
    val mockSpecificationService: SpecificationService         = mock[SpecificationService]

    val underTest = new DocumentationService(
      mockAPIDefinitionRepository,
      mockApiMicroserviceConnector,
      mockSpecificationService,
      mockServiceConfig
    )

    def generateRandomVersion: String = s"${Random.nextInt().abs}.${Random.nextInt().abs}"

    def theServiceIsRunningInSandboxMode() = when(mockServiceConfig.isSandbox).thenReturn(true)

    def theApiDefinitionWillBeReturned() = {
      when(mockAPIDefinitionRepository.fetchByServiceName(*))
        .thenReturn(successful(Some(apiDefinition)))
    }

    def noApiDefinitionWillBeReturned() = {
      when(mockAPIDefinitionRepository.fetchByServiceName(*))
        .thenReturn(successful(None))
    }

    def theApiMicroserviceWillReturnTheResource(response: WSResponse) = {
      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(*, *[ApiVersionNbr], *))
        .thenReturn(successful(response))
    }
  }

  "fetchApiDocumentationResource" should {

    "return the resource fetched from microservice when the API version exists" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resource"))

      result.header.status should be(Status.OK)
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResourceByUrl(*, eqTo(ApiVersionNbr("1.0")), eqTo("resource"))
    }

    "return the resource with given Content-Type when header is present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resource"))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/text"))
    }

    "return the resource with Proxy Safe Content-Type when content type is present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resource"))

      result.header.status should be(Status.OK)
      result.header.headers.get(PROXY_SAFE_CONTENT_TYPE) should be(Some("application/text"))
    }

    "return the resource with default Content-Type when header is not present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(chunkedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resource"))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/octet-stream"))
      result.header.headers.get(PROXY_SAFE_CONTENT_TYPE) should be(Some("application/octet-stream"))
    }

    "fail with internal server error when microservice returns an error" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(notFoundResponse)

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resourceNotThere"))
      }
    }

    "fail when local API microservice returns an internal server error" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(internalServerErrorResponse)

      intercept[InternalServerException] {
        await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resourceNotThere"))
      }
    }

    "fail when API definition is not found" in new Setup {
      noApiDefinitionWillBeReturned()

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("1.0"), "resource"))
      }
    }

    "return the resource fetched from microservice when requesting a common resource" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, ApiVersionNbr("common"), "resource"))

      result.header.status should be(Status.OK)
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResourceByUrl(*, eqTo(ApiVersionNbr("common")), eqTo("resource"))
    }
  }
}
