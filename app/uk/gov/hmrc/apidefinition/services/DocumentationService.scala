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

package uk.gov.hmrc.apidefinition.services

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status._
import play.api.http.{HttpEntity, Status}
import play.api.libs.ws.{DefaultWSResponseHeaders, StreamedResponse, WSResponseHeaders}
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ApiMicroserviceConnector
import uk.gov.hmrc.apidefinition.repository.{APIDefinitionRepository, ResourceData, ResourceRepository}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DocumentationService @Inject()(apiDefinitionRepository: APIDefinitionRepository,
                                     apiMicroserviceConnector: ApiMicroserviceConnector,
                                     resourceRepository: ResourceRepository,
                                     config: AppConfig)(implicit val ec: ExecutionContext) {

  implicit val system: ActorSystem = ActorSystem("System")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String)(implicit hc: HeaderCarrier): Future[Result] = {

    for {
      streamedResponse <- fetchResource(serviceName, version, resource)
    } yield streamedResponse.headers.status match {
      case OK =>
        val contentType = streamedResponse.headers.headers.get("Content-Type").flatMap(_.headOption)
          .getOrElse("application/octet-stream")

        streamedResponse.headers.headers.get("Content-Length") match {
          case Some(Seq(length)) => Ok.sendEntity(HttpEntity.Streamed(streamedResponse.body, Some(length.toLong), Some(contentType)))
          case _ => Ok.chunked(streamedResponse.body).as(contentType)
        }
      case NOT_FOUND => throw newNotFoundException(serviceName, version, resource)
      case status => throw newInternalServerException(serviceName, version, resource, status)
    }
  }


  private def fetchResource(serviceName: String, version: String, resource: String
                           )(implicit hc: HeaderCarrier): Future[StreamedResponse] = {

    def fetchStoredResource = resourceRepository.fetch(serviceName, version, resource)

    def fetchLocalResource(serviceBaseUrl: String): Future[StreamedResponse] =
      apiMicroserviceConnector.fetchApiDocumentationResourceByUrl(serviceBaseUrl, version, resource)

    def storeResponseWhenOk(response: StreamedResponse): Future[StreamedResponse] =
      response.headers.status match {
        case OK =>
          for {
            content <- extractContentFromResponse(response)
            _ <- resourceRepository.save(ResourceData(serviceName, version, resource, content))
          } yield createStreamedResponse(response.headers, content) // Create new streamedResponse to cater for read-once streams
        case _ => Future.successful(response)
      }

    fetchStoredResource.map {
      case Some(resourceData) =>
        val source = Source[ByteString](Seq(ByteString.fromArray(resourceData.contents)).to)

        val headers = Map("Content-Length" -> Seq(resourceData.contents.length.toString))
        StreamedResponse(DefaultWSResponseHeaders(Status.OK, headers), source)

      case _ =>
        throw new NotFoundException(s"Stored resource not found. Fallback required: $serviceName, $version, $resource")
    }.recoverWith {
      case e =>
        Logger.error("Fallback to retrieve local resource following exception.", e)

        for {
          api <- apiDefinitionRepository.fetchByServiceName(serviceName)
          response <- fetchLocalResource(api.get.serviceBaseUrl)  // TODO - solve get....)
          storedResponse <- storeResponseWhenOk(response)
        } yield storedResponse
    }
  }


  private def extractContentFromResponse(response: StreamedResponse): Future[Array[Byte]] = {
    response.body
      .runWith(Sink.reduce[ByteString](_ ++ _))
      .map { r: ByteString => r.toArray[Byte] }
  }

  private def createStreamedResponse(headers: WSResponseHeaders, content: Array[Byte]): StreamedResponse = {
    val source = Source.single(ByteString(content))
    StreamedResponse(headers, source)
  }

  private def newInternalServerException(serviceName: String, version: String, resource: String, status: Int) = {
    new InternalServerException(s"Error (status $status) downloading $resource for $serviceName $version")
  }

  private def newNotFoundException(serviceName: String, version: String, resource: String) = {
    new NotFoundException(s"$resource not found for $serviceName $version")
  }

}
