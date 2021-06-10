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

package uk.gov.hmrc.apidefinition.service

import java.util

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.Status
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseBodyPart, CacheableHttpResponseHeaders, CacheableHttpResponseStatus, CacheableResponse}
import play.api.libs.ws.ahc.{AhcWSResponse, StandaloneAhcWSResponse}
import play.api.mvc.Result
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ApiMicroserviceConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.DocumentationService
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

import uk.gov.hmrc.apidefinition.utils.Utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._
import scala.util.Random
import uk.gov.hmrc.apidefinition.services.SpecificationService
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import akka.stream.Materializer

class DocumentationServiceSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Utils {
  import DocumentationService.PROXY_SAFE_CONTENT_TYPE

  val serviceName = "hello-world"
  val version = "1.0"
  val serviceUrl = "http://localhost"
  val productionV1Availability: APIAvailability = APIAvailability(
    endpointsEnabled = true, PrivateAPIAccess(Seq.empty), loggedIn = false, authorised = false)
  val productionV2Availability: APIAvailability = APIAvailability(
    endpointsEnabled = true, PrivateAPIAccess(Seq.empty), loggedIn = false, authorised = false)
  val sandboxV2Availability: APIAvailability = APIAvailability(
    endpointsEnabled = true, PublicAPIAccess(),loggedIn = false, authorised = false)
  val sandboxV3Availability: APIAvailability = APIAvailability(
    endpointsEnabled = false, PublicAPIAccess(), loggedIn = false, authorised = false)
  val apiDefinition: APIDefinition = APIDefinition(
    serviceName = serviceName,
    serviceBaseUrl = serviceUrl,
    name = "Hello World",
    description = "Example",
    context = "hello",
    requiresTrust = Some(false),
    isTestSupport = Some(false),
    versions = Seq(
      APIVersion("1.0", APIStatus.STABLE, endpoints = Seq.empty, endpointsEnabled = None),
      APIVersion("2.0", APIStatus.BETA, endpoints = Seq.empty),
      APIVersion("3.0", APIStatus.ALPHA, endpoints = Seq.empty)
    ),
    lastPublishedAt = None
  )

  private val sampleFileSource: Source[ByteString, _] = createSourceFrom("hello")

  def createResponse(statusCode: Int,  headers: Map[String, String], fileSource: Source[ByteString, _]): AhcWSResponse = {
    val uri: Uri = Uri.create(serviceUrl)
    val status = new CacheableHttpResponseStatus(uri, statusCode , "", "")
    val defaultHeaders = new DefaultHttpHeaders()
    headers foreach { case (k, v) => defaultHeaders.add(k, v) }
    val responseHeaders = CacheableHttpResponseHeaders(trailingHeaders = false, headers = defaultHeaders)
    val bodyParts = util.Collections.emptyList[CacheableHttpResponseBodyPart]

    
    //TODO work out how to add source bytestream to bodyparts & copy headers
    AhcWSResponse(new StandaloneAhcWSResponse(CacheableResponse(status = status, headers = responseHeaders, bodyParts = bodyParts)))
  }

  private val streamedResource: AhcWSResponse =
    createResponse(Status.OK, Map("Content-Type" -> "application/text", "Content-Length" -> "hello".length.toString), sampleFileSource)
  private val chunkedResource: AhcWSResponse = createResponse(Status.OK, Map.empty, sampleFileSource)
  private val notFoundResponse: AhcWSResponse = createResponse(Status.NOT_FOUND, Map.empty, sampleFileSource)
  private val internalServerErrorResponse: AhcWSResponse = createResponse(Status.INTERNAL_SERVER_ERROR, Map.empty, sampleFileSource)
  
  implicit val materializer: Materializer = app.materializer
  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockApiMicroserviceConnector: ApiMicroserviceConnector = mock[ApiMicroserviceConnector]
    val mockServiceConfig: AppConfig = mock[AppConfig]
    val mockSpecificationService : SpecificationService = mock[SpecificationService]

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
      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(*, *, *))
        .thenReturn(successful(response))
    }
  }

  "fetchApiDocumentationResource" should {

    "return the resource fetched from microservice when the API version exists" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource"))

      result.header.status should be(Status.OK)
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResourceByUrl(*, eqTo("1.0"), eqTo("resource"))
    }

    "return the resource with given Content-Type when header is present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource"))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/text"))
    }

    "return the resource with Proxy Safe Content-Type when content type is present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource"))

      result.header.status should be(Status.OK)
      result.header.headers.get(PROXY_SAFE_CONTENT_TYPE) should be(Some("application/text"))
    }

    "return the resource with default Content-Type when header is not present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(chunkedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource"))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/octet-stream"))
      result.header.headers.get(PROXY_SAFE_CONTENT_TYPE) should be(Some("application/octet-stream"))
    }

    "fail with internal server error when microservice returns an error" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(notFoundResponse)

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere"))
      }
    }

    "fail when local API microservice returns an internal server error" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(internalServerErrorResponse)

      intercept[InternalServerException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere"))
      }
    }

    "fail when API definition is not found" in new Setup {
      noApiDefinitionWillBeReturned()

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource"))
      }
    }
  }
}
