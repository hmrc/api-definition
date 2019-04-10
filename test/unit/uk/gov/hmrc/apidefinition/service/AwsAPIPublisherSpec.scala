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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.services.AwsApiPublisher
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class AwsAPIPublisherSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  private val newAPIVersion = APIVersion(
      "1.0",
      APIStatus.PROTOTYPED,
      Some(PublicAPIAccess()),
      Seq(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED)))

  private val existingAPIVersion = APIVersion(
      "1.0",
      APIStatus.PROTOTYPED,
      Some(PublicAPIAccess()),
      Seq(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED)),
      awsApiId = Some("abc"))

  private def someAPIDefinition(version: APIVersion) = {
    APIDefinition(
      "calendar",
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      "calendar",
      Seq(version),
      None)
  }

  private trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new AwsApiPublisher(mock[AWSAPIPublisherConnector])
  }

  "publish" should {
    "create the API when none exists" in new Setup {
      when(underTest.awsAPIPublisherConnector.createAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful("123456"))

      val result: APIDefinition = await(underTest.publish(someAPIDefinition(newAPIVersion)))

      verify(underTest.awsAPIPublisherConnector).createAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])
      verify(underTest.awsAPIPublisherConnector, Mockito.times(0)).updateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier])
    }

    "adds the AWS Id to definition when new API is created" in new Setup {
      val awsId = UUID.randomUUID().toString
      when(underTest.awsAPIPublisherConnector.createAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful(awsId))

      val result: APIDefinition = await(underTest.publish(someAPIDefinition(newAPIVersion)))

      result.versions.head.awsApiId shouldBe Some(awsId)
    }

    "returns original API Definition if creation fails" in new Setup {
      val apiDefinition = someAPIDefinition(newAPIVersion)
      when(underTest.awsAPIPublisherConnector.createAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(Future.failed(new RuntimeException()))

      val result: APIDefinition = await(underTest.publish(apiDefinition))

      result shouldBe apiDefinition
    }

    "update the API when it already exists" in new Setup {
      when(underTest.awsAPIPublisherConnector.updateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful("123456"))

      val result: APIDefinition = await(underTest.publish(someAPIDefinition(existingAPIVersion)))

      verify(underTest.awsAPIPublisherConnector).updateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier])
      verify(underTest.awsAPIPublisherConnector, Mockito.times(0)).createAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])
    }

    "return the same API Definition after updating the API" in new Setup {
      when(underTest.awsAPIPublisherConnector.updateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful("123456"))
      val apiDefinition = someAPIDefinition(existingAPIVersion)

      val result: APIDefinition = await(underTest.publish(apiDefinition))

      result shouldBe apiDefinition
      result should not be theSameInstanceAs (apiDefinition)
    }

    "returns original API Definition if update fails" in new Setup {
      val apiDefinition = someAPIDefinition(existingAPIVersion)
      when(underTest.awsAPIPublisherConnector.updateAPI(any[String], any[WSO2SwaggerDetails])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException()))

      val result: APIDefinition = await(underTest.publish(apiDefinition))

      result shouldBe apiDefinition
    }
  }
}
