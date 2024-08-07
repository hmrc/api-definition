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

import java.net.URL
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._

import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import uk.gov.hmrc.apidefinition.services.EmailNotificationService
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class NotificationServiceSpec extends AsyncHmrcSpec {

  trait EmailNotificationSetup {

    def httpCallIsSuccessful(): Unit = {
      when(mockHTTPClient.post(eqTo(emailServiceURL))(*)).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(*)(*, *, *)).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](*, *)).thenReturn(successful(HttpResponse(OK, "")))
    }

    def emailServiceIsUnavailable(): Unit = {
      when(mockHTTPClient.post(eqTo(emailServiceURL))(*)).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(*)(*, *, *)).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](*, *)).thenReturn(successful(HttpResponse(NOT_FOUND, "")))
    }

    def callToEmailServiceFails(body: String): Unit = {
      when(mockHTTPClient.post(eqTo(emailServiceURL))(*)).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(*)(*, *, *)).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[HttpResponse](*, *)).thenReturn(successful(HttpResponse(INTERNAL_SERVER_ERROR, body)))
    }

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val emailServiceURL: URL        = url"https://localhost:9876/hmrc/email"
    val emailTemplateId: String     = UUID.randomUUID().toString
    val environmentName: String     = "Production"
    val emailAddresses: Set[String] = Set("foo@bar.com")

    val mockHTTPClient: HttpClientV2       = mock[HttpClientV2]
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

    val underTest: EmailNotificationService = new EmailNotificationService(mockHTTPClient, emailServiceURL, emailTemplateId, environmentName, emailAddresses)
  }

  "Email Notification Service" should {
    val apiName    = "API"
    val apiVersion = ApiVersionNbr("1.0")

    "make appropriate HTTP call email service to send message" in new EmailNotificationSetup {
      private val existingApiStatus = ApiStatus.ALPHA
      private val newApiStatus      = ApiStatus.BETA

      httpCallIsSuccessful()

      await(underTest.notifyOfStatusChange(apiName, apiVersion, existingApiStatus, newApiStatus))
    }

    "throw RuntimeException if email service is not available" in new EmailNotificationSetup {
      emailServiceIsUnavailable()

      val err: RuntimeException = intercept[RuntimeException] {
        await(underTest.notifyOfStatusChange(apiName, apiVersion, ApiStatus.ALPHA, ApiStatus.BETA))
      }
      err.getMessage shouldBe s"Unable to send email. Downstream endpoint not found: $emailServiceURL"
    }

    "throw RuntimeException if call to email service fails" in new EmailNotificationSetup {
      callToEmailServiceFails("""{"message":"Internal Error Occurred"}""")

      val err: RuntimeException = intercept[RuntimeException] {
        await(underTest.notifyOfStatusChange(apiName, apiVersion, ApiStatus.ALPHA, ApiStatus.BETA))
      }
      err.getMessage shouldBe "Internal Error Occurred"
    }

    "throw RuntimeException if call to email service fails other message" in new EmailNotificationSetup {
      callToEmailServiceFails("This is some other error message")

      val err: RuntimeException = intercept[RuntimeException] {
        await(underTest.notifyOfStatusChange(apiName, apiVersion, ApiStatus.ALPHA, ApiStatus.BETA))
      }
      err.getMessage shouldBe s"Unable send email. Unexpected error for url=$emailServiceURL status=500 response=This is some other error message"
    }
  }
}
