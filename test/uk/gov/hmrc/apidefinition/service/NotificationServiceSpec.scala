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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._

import org.mockito.ArgumentCaptor

import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import uk.gov.hmrc.apidefinition.models.APIStatus
import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, SendEmailRequest}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersionNbr

class NotificationServiceSpec extends AsyncHmrcSpec {

  trait EmailNotificationSetup {

    def httpCallIsSuccessful(): ArgumentCaptor[SendEmailRequest] = {
      val sendEmailRequestCaptor: ArgumentCaptor[SendEmailRequest] = ArgumentCaptor.forClass(classOf[SendEmailRequest])

      when(mockHTTPClient.POST[SendEmailRequest, HttpResponse](
        matches(emailServiceURL),
        sendEmailRequestCaptor.capture(),
        any[Seq[(String, String)]]
      )(*, *, *, *)).thenReturn(successful(HttpResponse(OK, "")))

      sendEmailRequestCaptor
    }

    def emailServiceIsUnavailable(): Unit =
      when(mockHTTPClient.POST[SendEmailRequest, HttpResponse](
        matches(emailServiceURL),
        any[SendEmailRequest],
        any[Seq[(String, String)]]
      )(*, *, *, *)).thenReturn(successful(HttpResponse(NOT_FOUND, "")))

    def callToEmailServiceFails(body: String): Unit = {
      when(mockHTTPClient.POST[SendEmailRequest, HttpResponse](
        matches(emailServiceURL),
        any[SendEmailRequest],
        any[Seq[(String, String)]]
      )(*, *, *, *)).thenReturn(successful(HttpResponse(INTERNAL_SERVER_ERROR, body)))
    }

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val emailServiceURL: String     = "https://localhost:9876/hmrc/email"
    val emailTemplateId: String     = UUID.randomUUID().toString
    val environmentName: String     = "Production"
    val emailAddresses: Set[String] = Set("foo@bar.com")

    val mockHTTPClient: HttpClient = mock[HttpClient]

    val underTest: EmailNotificationService = new EmailNotificationService(mockHTTPClient, emailServiceURL, emailTemplateId, environmentName, emailAddresses)
  }

  "Email Notification Service" should {
    val apiName    = "API"
    val apiVersion = ApiVersionNbr("1.0")

    "make appropriate HTTP call email service to send message" in new EmailNotificationSetup {
      private val existingAPIStatus = APIStatus.ALPHA
      private val newAPIStatus      = APIStatus.BETA

      private val requestCaptor: ArgumentCaptor[SendEmailRequest] = httpCallIsSuccessful()

      await(underTest.notifyOfStatusChange(apiName, apiVersion, existingAPIStatus, newAPIStatus))

      private val capturedRequest: SendEmailRequest = requestCaptor.getValue
      capturedRequest.to shouldBe emailAddresses
      capturedRequest.templateId shouldBe emailTemplateId
      capturedRequest.parameters.get("apiName") shouldBe Some(apiName)
      capturedRequest.parameters.get("apiVersion") shouldBe Some(apiVersion.toString)
      capturedRequest.parameters.get("currentStatus") shouldBe Some(existingAPIStatus.toString)
      capturedRequest.parameters.get("newStatus") shouldBe Some(newAPIStatus.toString)
      capturedRequest.parameters.get("environmentName") shouldBe Some(environmentName)
    }

    "throw RuntimeException if email service is not available" in new EmailNotificationSetup {
      emailServiceIsUnavailable()

      val err: RuntimeException = intercept[RuntimeException] {
        await(underTest.notifyOfStatusChange(apiName, apiVersion, APIStatus.ALPHA, APIStatus.BETA))
      }
      err.getMessage shouldBe s"Unable to send email. Downstream endpoint not found: $emailServiceURL"
    }

    "throw RuntimeException if call to email service fails" in new EmailNotificationSetup {
      callToEmailServiceFails("""{"message":"Internal Error Occurred"}""")

      val err: RuntimeException = intercept[RuntimeException] {
        await(underTest.notifyOfStatusChange(apiName, apiVersion, APIStatus.ALPHA, APIStatus.BETA))
      }
      err.getMessage shouldBe "Internal Error Occurred"
    }

    "throw RuntimeException if call to email service fails other message" in new EmailNotificationSetup {
      callToEmailServiceFails("This is some other error message")

      val err: RuntimeException = intercept[RuntimeException] {
        await(underTest.notifyOfStatusChange(apiName, apiVersion, APIStatus.ALPHA, APIStatus.BETA))
      }
      err.getMessage shouldBe s"Unable send email. Unexpected error for url=$emailServiceURL status=500 response=This is some other error message"
    }
  }
}
