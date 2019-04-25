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
import org.mockito.Mockito._
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
import scala.concurrent.Future._

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

  private def someAPIDefinition(version: APIVersion*): APIDefinition = {
    APIDefinition(
      "calendar",
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      "calendar",
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
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful(UUID.randomUUID().toString))

      val result: APIDefinition = await(underTest.publish(someAPIDefinition(newAPIVersion)))

      verify(underTest.awsAPIPublisherConnector).createOrUpdateAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])
    }

    "add the AWS Request Id to the API definition when an API is created or updated" in new Setup {
      val awsRequestId: String = UUID.randomUUID().toString
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(successful(awsRequestId))

      val result: APIDefinition = await(underTest.publish(someAPIDefinition(newAPIVersion)))

      result.versions.head.awsRequestId shouldBe Some(awsRequestId)
    }

    "returns original API Definition if creation fails" in new Setup {
      val apiDefinition: APIDefinition = someAPIDefinition(newAPIVersion)
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[WSO2SwaggerDetails])(any[HeaderCarrier])).thenReturn(Future.failed(new RuntimeException()))

      val result: APIDefinition = await(underTest.publish(apiDefinition))

      result shouldBe apiDefinition
    }
  }
}
