/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse}
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
import scala.reflect.io
import scala.util.Random

class DocumentationServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar with Utils {

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

  private val applicationRamlFile1Name = "application1.raml"
  private val applicationRamlFile2Name = "application2.raml"
  private val applicationWithNestedRamlFileName = "applicationWithNestedRaml.raml"
  private val applicationWithCircularDependencyRamlFileName = "applicationWithCircularDependencyRaml.raml"
  private val applicationWithCapsInExtensionFileName = "applicationWithCapsInExtension.RaMl"
  private val applicationWithUsesInRamlFileName = "applicationWithUsesThatAreNotAbsolute.raml"
  private val applicationWithNestedUsesInRamlFileName = "applicationWithNestedUsesInRaml.raml"
  private val applicationRamlFile1Source: Source[ByteString, _] = createSourceFrom(applicationRamlFile1Name)
  private val applicationRamlFile2Source: Source[ByteString, _] = createSourceFrom(applicationRamlFile2Name)
  private val applicationWithNestedRamlFileSource: Source[ByteString, _] = createSourceFrom(applicationWithNestedRamlFileName)
  private val applicationWithCircularDependencyRamlFileSource: Source[ByteString, _] = createSourceFrom(applicationWithCircularDependencyRamlFileName)
  private val applicationWithCapsInExtensionFileSource: Source[ByteString, _] = createSourceFrom(applicationWithCapsInExtensionFileName)
  private val applicationWithUsesInRamlFileSource: Source[ByteString, _] = createSourceFrom(applicationWithUsesInRamlFileName)
  private val applicationWithNestedUsesInRamlFileSource: Source[ByteString, _] = createSourceFrom(applicationWithNestedUsesInRamlFileName)
  private val streamedResource = StreamedResponse(DefaultWSResponseHeaders(Status.OK, Map("Content-Type" -> Seq("application/text"), "Content-Length" -> Seq("hello".length.toString))), sampleFileSource)
  private val chunkedResource = StreamedResponse(DefaultWSResponseHeaders(Status.OK, Map.empty), sampleFileSource)
  private val notFoundResponse = StreamedResponse(DefaultWSResponseHeaders(Status.NOT_FOUND, Map.empty), sampleFileSource)
  private val internalServerErrorResponse = StreamedResponse(DefaultWSResponseHeaders(Status.INTERNAL_SERVER_ERROR, Map.empty), sampleFileSource)

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

    def theApiMicroserviceWillReturnTheResource(response: StreamedResponse): OngoingStubbing[Future[StreamedResponse]] = {
      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(anyString, anyString, anyString))
        .thenReturn(Future.successful(response))
    }

//    def theApiMicroserviceWillReturnTheNamedResourceByUrl(resource: String, version: String): OngoingStubbing[Future[StreamedResponse]] = {
//      val source: Source[ByteString, _] = createSourceFrom(resource)
//      val streamedResource = StreamedResponse(DefaultWSResponseHeaders(Status.OK, Map("Content-Type" -> Seq("application/text"))), source)
//      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(anyString, meq(version), meq(resource)))
//        .thenReturn(Future.successful(streamedResource))
//    }
//
    def theApiMicroserviceWillReturnTheNamedResourceByUrlAsStreamWithLength(resource: String, version: String): OngoingStubbing[Future[StreamedResponse]] = {
      val source: Source[ByteString, _] = createSourceFrom(resource)
      val file: io.File = io.File(getClass.getResource("/" + resource).getPath)
      file.length

      val streamedResource = StreamedResponse(
          DefaultWSResponseHeaders(
            Status.OK
            , Map("Content-Type" -> Seq("application/text"), "Content-Length" -> Seq(length.toString))
          )
        , source
        )
      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(anyString, meq(version), meq(resource)))
        .thenReturn(Future.successful(streamedResource))
    }

//    def theApiMicroserviceWillReturnTheResourceByUrl(response: StreamedResponse, version: String): OngoingStubbing[Future[StreamedResponse]] = {
//      when(mockApiMicroserviceConnector.fetchApiDocumentationResourceByUrl(anyString, meq(version), anyString))
//        .thenReturn(Future.successful(response))
//    }
//
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

//    "return the resource with given Content-Type when Content-Length header is present" in new Setup {
//      theApiDefinitionWillBeReturned()
//      theApiMicroserviceWillReturnTheResource("resource", "1.0")
//
//
//      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))
//
//      result.header.status should be(Status.OK)
//      result.body.contentType should be(Some("application/text"))
//    }

    "return the resource with default Content-Type when header is not present" in new Setup {
      theApiDefinitionWillBeReturned()
      theApiMicroserviceWillReturnTheResource(chunkedResource)

      val result: Result = await(underTest.fetchApiDocumentationResource(serviceName, "1.0", "resource")(hc))

      result.header.status should be(Status.OK)
      result.body.contentType should be(Some("application/octet-stream"))
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
