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

package uk.gov.hmrc.apidefinition.services

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.libs.ws.WSResponse
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.ApiMicroserviceConnector
import uk.gov.hmrc.apidefinition.models.{APIDefinition, APIVersion}
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

object DocumentationService {
  val PROXY_SAFE_CONTENT_TYPE = "Proxy-Safe-Content-Type"
}
@Singleton
class DocumentationService @Inject()(apiDefinitionRepository: APIDefinitionRepository,
                                     apiMicroserviceConnector: ApiMicroserviceConnector,
                                     specificationService : SpecificationService,
                                     config: AppConfig)
                                    (implicit val ec: ExecutionContext,
                                     val mat: Materializer) {

  import DocumentationService._

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String): Future[Result] = {
    def createProxySafeContentType(contentType: String): (String, String) = ((PROXY_SAFE_CONTENT_TYPE, contentType))

    // TODO: ebridge - remove when routed via api-platform-microservice
    if(resource == "packed(application.raml)") {
      specificationService.fetchApiSpecification(serviceName, version)
        .map(json => Ok(json))
    } else {
      for {
        streamedResponse <- fetchResource(serviceName, version, resource)
      } yield streamedResponse.status match {
        case OK =>
          val contentType = streamedResponse.contentType

          streamedResponse.headers.get("Content-Length") match {
            case Some(Seq(length)) => Ok.sendEntity(HttpEntity.Streamed(streamedResponse.bodyAsSource, Some(length.toLong), Some(contentType)))
              .withHeaders(createProxySafeContentType(contentType))

            case _ => Ok.chunked(streamedResponse.bodyAsSource).as(contentType)
              .withHeaders(createProxySafeContentType(contentType))
          }
        case NOT_FOUND => throw newNotFoundException(serviceName, version, resource)
        case status => throw newInternalServerException(serviceName, version, resource, status)
      }
    }
  }

  //noinspection ScalaStyle
  private def fetchResource(serviceName: String, version: String, resource: String): Future[WSResponse] = {

    def fetchResourceFromMicroservice(serviceBaseUrl: String): Future[WSResponse] =
      apiMicroserviceConnector.fetchApiDocumentationResourceByUrl(serviceBaseUrl, version, resource)

    def getApiDefinitionOrThrow: Future[APIDefinition] = {
      import cats.implicits._

      lazy val failure = Future.failed[APIDefinition](new NotFoundException(s"$serviceName not found"))

      apiDefinitionRepository.fetchByServiceName(serviceName).flatMap( _.fold(failure)(_.pure[Future]) )
    }

    def getApiVersionOrThrow(apiDefinition: APIDefinition): Future[APIVersion] = {
      import cats.implicits._

      val failure = Future.failed[APIVersion](new NotFoundException(s"Version $version of $serviceName not found"))
      val oVersion = apiDefinition.versions.find(_.version == version)

      oVersion.fold(failure)(v => v.pure[Future])
    }

    /*
    ** Start here
     */
    for {
      api <- getApiDefinitionOrThrow
      _ <- getApiVersionOrThrow(api)

      serviceBaseUrl = api.serviceBaseUrl
      response <- fetchResourceFromMicroservice(serviceBaseUrl)
    } yield response
  }

  private def newInternalServerException(serviceName: String, version: String, resource: String, status: Int) = {
    new InternalServerException(s"Error (status $status) downloading $resource for $serviceName $version")
  }

  private def newNotFoundException(serviceName: String, version: String, resource: String) = {
    new NotFoundException(s"$resource not found for $serviceName $version")
  }
}

