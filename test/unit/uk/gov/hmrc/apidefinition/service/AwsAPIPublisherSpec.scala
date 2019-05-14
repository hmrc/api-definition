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

import java.util.UUID

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.AwsApiPublisher
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class AwsAPIPublisherSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  private val newAPIVersion = APIVersion(
      "2.0",
      APIStatus.PROTOTYPED,
      Some(PublicAPIAccess()),
      Seq(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED)))

  private val apiContext = "calendar"
  private val apiName = "Calendar API"
  private val httpServiceBaseUrl = "http://calendar"
  private val httpsServiceBaseUrl = "https://calendar"
  private def someAPIDefinition(serviceBaseUrl: String, version: APIVersion*): APIDefinition = {
    APIDefinition(
      "calendar",
      serviceBaseUrl,
      apiName,
      "My Calendar API",
      apiContext,
      version,
      None)
  }

  private trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new AwsApiPublisher(mock[AWSAPIPublisherConnector], mock[APIDefinitionRepository])
    when(underTest.apiDefinitionRepository.fetchByServiceName(any[String])).thenReturn(successful(None))
  }

  "publish" should {
    "create or update the API in AWS" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[WSO2SwaggerDetails] = ArgumentCaptor.forClass(classOf[WSO2SwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], swaggerDetailsCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.publish(someAPIDefinition(httpsServiceBaseUrl, newAPIVersion)))

      verify(underTest.awsAPIPublisherConnector).createOrUpdateAPI(ArgumentMatchers.eq("calendar--2.0"), any[WSO2SwaggerDetails])(any[HeaderCarrier])
      val swaggerDetails: WSO2SwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some("calendar")
      swaggerDetails.info.title shouldBe apiName
    }

    "populate correctly the host from service base URLs using HTTP" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[WSO2SwaggerDetails] = ArgumentCaptor.forClass(classOf[WSO2SwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], swaggerDetailsCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.publish(someAPIDefinition(httpServiceBaseUrl, newAPIVersion)))

      val swaggerDetails: WSO2SwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some("calendar")
    }

    "add the AWS Request Id to the API definition when an API is created or updated" in new Setup {
      val awsRequestId: String = UUID.randomUUID().toString
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful(awsRequestId))

      val result: APIDefinition = await(underTest.publish(someAPIDefinition(httpsServiceBaseUrl, newAPIVersion)))

      result.versions.head.awsRequestId shouldBe Some(awsRequestId)
    }

    "return original API Definition if creation fails" in new Setup {
      val apiDefinition: APIDefinition = someAPIDefinition(httpsServiceBaseUrl, newAPIVersion)
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException()))

      val result: APIDefinition = await(underTest.publish(apiDefinition))

      result shouldBe apiDefinition
    }
  }

  "delete" should {
    "delete the API in AWS" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(any[String])(any[HeaderCarrier])).thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.delete(someAPIDefinition(httpsServiceBaseUrl, newAPIVersion)))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"$apiContext--2.0")(hc)
    }

    "return unit if deletion fails" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(any[String])(any[HeaderCarrier])).thenReturn(Future.failed(new RuntimeException()))

      val result: Unit = await(underTest.delete(someAPIDefinition(httpsServiceBaseUrl, newAPIVersion)))

      result shouldBe ()
    }
  }
}
