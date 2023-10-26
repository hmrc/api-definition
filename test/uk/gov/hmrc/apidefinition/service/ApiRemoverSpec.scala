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

import play.api.Logger
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.services.ApiRemover
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class ApiRemoverSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    
    val mockLogger: Logger       = mock[Logger]
    val mockPublisherConnector       = mock[AWSAPIPublisherConnector]
    val mockAppConfig: AppConfig = mock[AppConfig]

    val underTest = new ApiRemover(mockPublisherConnector, mockAppConfig) {
      override val logger: Logger = mockLogger
    }
  }
  "deleteUnusedApis" should {
    "call AWSAPIPublisherConnector.deleteAPI for each unused API and log message on success" in new Setup {
      when(mockAppConfig.apisToRemove).thenReturn(List("api1", "api2", "api3"))
      when(mockPublisherConnector.deleteAPI(*)(*)).thenReturn(successful("request ID"))

      val result: Unit = await(underTest.deleteUnusedApis())
      result shouldBe ()

      verify(mockPublisherConnector, times(3)).deleteAPI(*)(*)
      verify(mockPublisherConnector).deleteAPI("api1")(hc)
      verify(mockPublisherConnector).deleteAPI("api2")(hc)
      verify(mockPublisherConnector).deleteAPI("api3")(hc)
      verifyNoMoreInteractions(mockPublisherConnector)
      
      verify(mockLogger).info(s"Attempting to delete 3 unused APIs.")
      verify(mockLogger).info(s"api1 successfully deleted. (Request ID: request ID)")
      verify(mockLogger).info(s"api2 successfully deleted. (Request ID: request ID)")
      verify(mockLogger).info(s"api3 successfully deleted. (Request ID: request ID)")
      verifyNoMoreInteractions(mockLogger)
    }

    "call AWSAPIPublisherConnector.deleteAPI for each unused API and log message on failure" in new Setup {

      val error = UpstreamErrorResponse.apply("error1", 500, 1)

      when(mockAppConfig.apisToRemove).thenReturn(List("api1", "api2", "api3"))
      when(mockPublisherConnector.deleteAPI(*)(*)).thenReturn(failed(error))

      val result: Unit = await(underTest.deleteUnusedApis())
      result shouldBe ()

      verify(mockPublisherConnector).deleteAPI("api1")(hc)
      verify(mockPublisherConnector).deleteAPI("api2")(hc)
      verify(mockPublisherConnector).deleteAPI("api3")(hc)
      verify(mockPublisherConnector, times(3)).deleteAPI(*)(*)
      verifyNoMoreInteractions(mockPublisherConnector)

      verify(mockLogger).info(s"Attempting to delete 3 unused APIs.")
      verify(mockLogger).warn(s"api1 delete failed.", error)
      verify(mockLogger).warn(s"api2 delete failed.", error)
      verify(mockLogger).warn(s"api3 delete failed.", error)
      verifyNoMoreInteractions(mockLogger)
    }

    "do nothing when the list of APIs to remove is empty" in new Setup {
      when(mockAppConfig.apisToRemove).thenReturn(List())

      underTest.deleteUnusedApis()

      verifyZeroInteractions(mockPublisherConnector)
    }

    "log the number of APIs to be removed" in new Setup {
      when(mockAppConfig.apisToRemove).thenReturn(List("1", "2", "3", "4"))
      when(mockPublisherConnector.deleteAPI(*)(*)).thenReturn(successful(""))

      underTest.deleteUnusedApis()

      verify(mockLogger).info(s"Attempting to delete 4 unused APIs.")
    }
  }
}
