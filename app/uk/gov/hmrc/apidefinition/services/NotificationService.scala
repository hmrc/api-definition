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

import play.api.Logger
import play.api.libs.json.{Json, OFormat}
import play.mvc.Http.Status.NOT_FOUND
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait NotificationService {
  def notifyOfStatusChange(apiName: String, apiVersion: String, existingAPIStatus: APIStatus, newAPIStatus: APIStatus)
                          (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit]
}

class LoggingNotificationService extends NotificationService {

  def notifyOfStatusChange(apiName: String, apiVersion: String, existingAPIStatus: APIStatus, newAPIStatus: APIStatus)
                          (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    Future {
      Logger.info(s"API [$apiName] Version [$apiVersion] Status has changed from [$existingAPIStatus] to [$newAPIStatus]")
    }
  }
}

class EmailNotificationService(httpClient: HttpClient,
                               val emailServiceURL: String,
                               val emailTemplateId: String,
                               val emailAddresses: Set[String]) extends NotificationService {
  case class SendEmailRequest(to: Set[String],
                              templateId: String,
                              parameters: Map[String, String],
                              force: Boolean = false,
                              auditData: Map[String, String] = Map.empty,
                              eventUrl: Option[String] = None)

  object SendEmailRequest {
    implicit val sendEmailRequestFormat: OFormat[SendEmailRequest] = Json.format[SendEmailRequest]
  }

  override def notifyOfStatusChange(apiName: String, apiVersion: String, existingAPIStatus: APIStatus, newAPIStatus: APIStatus)
                                   (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    sendEmail(new SendEmailRequest(emailAddresses, emailTemplateId, Map.empty)).flatMap(_ => Future.successful())
  }

  private def sendEmail(payload: SendEmailRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    def extractError(response: HttpResponse): RuntimeException = {
      Try(response.json \ "message") match {
        case Success(jsValue) => new RuntimeException(jsValue.as[String])
        case Failure(_) => new RuntimeException(
          s"Unable send email. Unexpected error for url=$emailServiceURL status=${response.status} response=${response.body}")
      }
    }

    httpClient.POST[SendEmailRequest, HttpResponse](emailServiceURL, payload)
      .map { response =>
        Logger.info(s"Sent '${payload.templateId}' to: ${payload.to.mkString(",")} with response: ${response.status}")
        response.status match {
          case status if status >= 200 && status <= 299 => response
          case NOT_FOUND => throw new RuntimeException(s"Unable to send email. Downstream endpoint not found: $emailServiceURL")
          case _ => throw extractError(response)
        }
      }
  }
}
