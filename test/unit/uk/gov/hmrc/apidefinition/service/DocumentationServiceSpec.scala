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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers.{any, anyString, eq => meq}
import org.mockito.Mockito.{verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.{Answer, OngoingStubbing}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.ws.WSResponse
import play.api.libs.ws.DefaultWSResponseHeaders
import play.api.mvc.Result
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ApiMicroserviceConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.DocumentationService
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import uk.gov.hmrc.play.test.UnitSpec
import unit.uk.gov.hmrc.apidefinition.Utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class DocumentationServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar with Utils {
  import DocumentationService.PROXY_SAFE_CONTENT_TYPE

  val serviceName = "hello-world"
  val version = "1.0"
  val serviceUrl = "http://localhost"
  val productionV1Availability = APIAvailability(
    endpointsEnabled = true, PrivateAPIAccess(Seq.empty), loggedIn = false, authorised = false)
  val productionV2Availability = APIAvailability(
    endpointsEnabled = true, PrivateAPIAccess(Seq.empty), loggedIn = false, authorised = false)
  val sandboxV2Availability = APIAvailability(
    endpointsEnabled = true, PublicAPIAccess(),loggedIn = false, authorised = false)
  val sandboxV3Availability = APIAvailability(
    endpointsEnabled = false, PublicAPIAccess(), loggedIn = false, authorised = false)
  val apiDefinition = APIDefinition(
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

  private val streamedResource = new WSResponse(
    DefaultWSResponseHeaders(Status.OK, Map("Content-Type" -> Seq("application/text"), "Content-Length" -> Seq("hello".length.toString)))
    , sampleFileSource)
  private val chunkedResource = new WSResponse(DefaultWSResponseHeaders(Status.OK, Map.empty), sampleFileSource)
  private val notFoundResponse = new WSResponse(DefaultWSResponseHeaders(Status.NOT_FOUND, Map.empty), sampleFileSource)
  private val internalServerErrorResponse = new WSResponse(DefaultWSResponseHeaders(Status.INTERNAL_SERVER_ERROR, Map.empty), sampleFileSource)

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockApiMicroserviceConnector: ApiMicroserviceConnector = mock[ApiMicroserviceConnector]
    val mockServiceConfig: AppConfig = mock[AppConfig]

    val underTest = new DocumentationService(
      mockAPIDefinitionRepository,
      mockApiMicroserviceConnector,
      mockServiceConfig
    )

    def generateRandomVersion: String = s"${Random.nextInt().abs}.${Random.nextInt().abs}"

    def theServiceIsRunningInSandboxMode(): OngoingStubbing[Boolean] = when(mockServiceConfig.isSandbox).thenReturn(true)

    def theApiDefinitionWillBeReturned() = {
      when(mockAPIDefinitionRepository.fetchByServiceName(any()))
        .thenReturn(Future.successful(Some(apiDefinition)))
    }

    def noApiDefinitionWillBeReturned() = {
      when(mockAPIDefinitionRepository.fetchByServiceName(any()))
        .thenReturn(Future.successful(None))
    }

    def theApiMicroserviceWillReturnTheResource(response: WSResponse): OngoingStubbing[Future[WSResponse]] = {
      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(anyString, anyString, anyString))
        .thenReturn(Future.successful(response))
    }

    def answer[T](f: InvocationOnMock => T): Answer[T] = {
      new Answer[T] {
        override def answer(invocation: InvocationOnMock): T = f(invocation)
      }
    }
  }

  "fetchApiDocumentationResource" should {

    "return the resource fetched from microservice when the API version exists" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      verify(mockApiMicroserviceConnector).fetchApiDocumentationResourceByUrl(any(), meq("1.0"), meq("resource"))
    }

    "return the resource with given Content-Type when header is present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/text"))
    }

    "return the resource with Proxy Safe Content-Type when content type is present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(streamedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      result.header.headers.get(PROXY_SAFE_CONTENT_TYPE) should be(Some("application/text"))
    }

    "return the resource with default Content-Type when header is not present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(chunkedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/octet-stream"))
      result.header.headers.get(PROXY_SAFE_CONTENT_TYPE) should be(Some("application/octet-stream"))
    }

    "fail with internal server error when microservice returns an error" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(notFoundResponse)

      intercept[NotFoundException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when local API microservice returns an internal server error" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(internalServerErrorResponse)

      intercept[InternalServerException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resourceNotThere")(hc))
      }
    }

    "fail when API definition is not found" in new Setup {
      noApiDefinitionWillBeReturned()

      intercept[IllegalArgumentException] {
        await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))
      }
    }
  }
}
