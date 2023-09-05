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

package uk.gov.hmrc.apidefinition.services

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.libs.json.{Json, OFormat}
import play.mvc.Http.Status.NOT_FOUND
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

trait NotificationService {
  val environmentName: String

  def notifyOfStatusChange(
      apiName: String,
      apiVersion: ApiVersionNbr,
      existingApiStatus: ApiStatus,
      newApiStatus: ApiStatus
    )(implicit ec: ExecutionContext,
      headerCarrier: HeaderCarrier
    ): Future[Unit]
}

class LoggingNotificationService(override val environmentName: String) extends NotificationService with ApplicationLogger {

  def notifyOfStatusChange(
      apiName: String,
      apiVersion: ApiVersionNbr,
      existingApiStatus: ApiStatus,
      newApiStatus: ApiStatus
    )(implicit ec: ExecutionContext,
      headerCarrier: HeaderCarrier
    ): Future[Unit] = {
    Future {
      logger.info(s"API [$apiName] Version [$apiVersion] Status has changed from [$existingApiStatus] to [$newApiStatus] in [$environmentName] environment")
    }
  }
}

class EmailNotificationService(
    httpClient: HttpClient,
    val emailServiceURL: String,
    val emailTemplateId: String,
    override val environmentName: String,
    val emailAddresses: Set[String]
  ) extends NotificationService with ApplicationLogger {

  override def notifyOfStatusChange(
      apiName: String,
      apiVersion: ApiVersionNbr,
      existingApiStatus: ApiStatus,
      newApiStatus: ApiStatus
    )(implicit ec: ExecutionContext,
      headerCarrier: HeaderCarrier
    ): Future[Unit] = {
    sendEmail(
      new SendEmailRequest(
        emailAddresses,
        emailTemplateId,
        Map(
          "apiName"         -> apiName,
          "apiVersion"      -> apiVersion.value,
          "currentStatus"   -> existingApiStatus.toString,
          "newStatus"       -> newApiStatus.toString,
          "environmentName" -> environmentName
        )
      )
    ).flatMap(_ => successful(()))
  }

  private def sendEmail(payload: SendEmailRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    def extractError(response: HttpResponse): RuntimeException = {
      Try(response.json \ "message") match {
        case Success(jsValue) => new RuntimeException(jsValue.as[String])
        case Failure(_)       => new RuntimeException(
            s"Unable send email. Unexpected error for url=$emailServiceURL status=${response.status} response=${response.body}"
          )
      }
    }

    httpClient.POST[SendEmailRequest, HttpResponse](emailServiceURL, payload)
      .map { response =>
        logger.info(s"Sent '${payload.templateId}' to: ${payload.to.mkString(",")} with response: ${response.status}")
        response.status match {
          case status if status >= 200 && status <= 299 => response
          case NOT_FOUND                                => throw new RuntimeException(s"Unable to send email. Downstream endpoint not found: $emailServiceURL")
          case _                                        => throw extractError(response)
        }
      }
  }
}

case class SendEmailRequest(to: Set[String], templateId: String, parameters: Map[String, String])

object SendEmailRequest {
  implicit val sendEmailRequestFormat: OFormat[SendEmailRequest] = Json.format[SendEmailRequest]
}
