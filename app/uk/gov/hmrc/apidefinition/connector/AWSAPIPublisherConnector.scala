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

package uk.gov.hmrc.apidefinition.connector

import javax.inject.Inject
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.{Json, Reads}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.AWSSwaggerDetails
import uk.gov.hmrc.apidefinition.models.JsonFormatters._

object AWSAPIPublisherConnector {
  // Deliberately upper case RequestId !
  case class RequestId(RequestId: String)

  implicit val requestIdReads: Reads[RequestId] = Json.reads[RequestId]
}

class AWSAPIPublisherConnector @Inject() (
    http: HttpClient,
    environment: Environment,
    appContext: AppConfig,
    val runModeConfiguration: Configuration,
    servicesConfig: ServicesConfig
  )(implicit val ec: ExecutionContext
  ) {
  import AWSAPIPublisherConnector._

  protected def mode: Mode = environment.mode

  val serviceBaseUrl: String         = s"""${servicesConfig.baseUrl("aws-gateway")}/v1/api"""
  val awsApiKey: String              = runModeConfiguration.get[String]("awsApiKey")
  val apiKeyHeaderName               = "x-api-key"
  val headers: Seq[(String, String)] = Seq(CONTENT_TYPE -> JSON, apiKeyHeaderName -> awsApiKey)

  def createOrUpdateAPI(apiName: String, awsSwaggerDetails: AWSSwaggerDetails)(implicit hc: HeaderCarrier): Future[String] = {
    val headersWithoutAuthorization: HeaderCarrier = hc.copy(authorization = None)

    http.PUT[AWSSwaggerDetails, Either[UpstreamErrorResponse, RequestId]](s"$serviceBaseUrl/$apiName", awsSwaggerDetails, headers)(
      implicitly,
      implicitly,
      headersWithoutAuthorization,
      implicitly
    ) flatMap {
      case Right(RequestId(value)) => successful(value)
      case Left(err)               => failed(err)
    }
  }

  def deleteAPI(apiName: String)(implicit hc: HeaderCarrier): Future[String] = {
    val headersWithoutAuthorization: HeaderCarrier = hc
      .copy(authorization = None)
      .withExtraHeaders(apiKeyHeaderName -> awsApiKey)

    http.DELETE[Either[UpstreamErrorResponse, RequestId]](s"$serviceBaseUrl/$apiName")(implicitly, headersWithoutAuthorization, implicitly) flatMap {
      case Right(RequestId(value)) => successful(value)
      case Left(err)               => failed(err)
    }
  }
}
