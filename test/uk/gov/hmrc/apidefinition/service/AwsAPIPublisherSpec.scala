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

package uk.gov.hmrc.apidefinition.service

import java.util.UUID

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.AwsApiPublisher
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class AwsAPIPublisherSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  private def anAPIVersion(version: String, status: APIStatus = APIStatus.PROTOTYPED) = APIVersion(
      version,
      status,
      Some(PublicAPIAccess()),
      Seq(
        Endpoint(
          "/today",
          "Get Today's Date",
          HttpMethod.GET,
          AuthType.NONE,
          ResourceThrottlingTier.UNLIMITED)))

  private val host = UUID.randomUUID().toString
  private def someAPIDefinition(name: String = UUID.randomUUID().toString,
                                context: String = UUID.randomUUID().toString,
                                serviceBaseUrl: String = s"https://$host",
                                versions: Seq[APIVersion] = Seq(anAPIVersion("2.0"))): APIDefinition = {
    APIDefinition(
      UUID.randomUUID().toString,
      serviceBaseUrl,
      name,
      UUID.randomUUID().toString,
      context,
      versions,
      None)
  }

  private trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new AwsApiPublisher(mock[AWSAPIPublisherConnector], mock[APIDefinitionRepository])
    when(underTest.apiDefinitionRepository.fetchByServiceName(any[String])).thenReturn(successful(None))
  }

  "publishAll" should {
    "create or update all the APIs in AWS" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], swaggerDetailsCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition1: APIDefinition = someAPIDefinition("API 1")
      val apiDefinition2: APIDefinition = someAPIDefinition("API 2")

      await(underTest.publishAll(Seq(apiDefinition1, apiDefinition2)))

      verify(underTest.awsAPIPublisherConnector, times(2)).createOrUpdateAPI(any[String], any[AWSSwaggerDetails])(any[HeaderCarrier])
      val swaggerDetails: Seq[AWSSwaggerDetails] = swaggerDetailsCaptor.getAllValues.asScala
      swaggerDetails.head.info.title shouldBe apiDefinition1.name
      swaggerDetails(1).info.title shouldBe apiDefinition2.name
    }
  }

  "publish" should {
    "create or update the API in AWS" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], swaggerDetailsCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: APIDefinition = someAPIDefinition()

      await(underTest.publish(apiDefinition))

      verify(underTest.awsAPIPublisherConnector)
        .createOrUpdateAPI(ArgumentMatchers.eq(s"${apiDefinition.context}--2.0"), any[AWSSwaggerDetails])(any[HeaderCarrier])
      val swaggerDetails: AWSSwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some(host)
      swaggerDetails.info.title shouldBe apiDefinition.name
    }

    "populate correctly the host from service base URLs using HTTP" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], swaggerDetailsCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.publish(someAPIDefinition(serviceBaseUrl = s"http://$host")))

      val swaggerDetails: AWSSwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some(host)
    }

    "call delete endpoint for RETIRED versions" in new Setup {
      val apiDefinition: APIDefinition = someAPIDefinition(versions = Seq(anAPIVersion("1.0", APIStatus.RETIRED), anAPIVersion("2.0")))

      when(underTest.awsAPIPublisherConnector.deleteAPI(any[String])(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(any[String], any[AWSSwaggerDetails])(any[HeaderCarrier]))
        .thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.publish(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--1.0")(hc)
      verify(underTest.awsAPIPublisherConnector)
        .createOrUpdateAPI(ArgumentMatchers.eq(s"${apiDefinition.context}--2.0"), any[AWSSwaggerDetails])(any[HeaderCarrier])
    }
  }

  "delete" should {
    "delete the API in AWS" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(any[String])(any[HeaderCarrier])).thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: APIDefinition = someAPIDefinition()

      await(underTest.delete(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--2.0")(hc)
    }

    "delete multiple versions of the API in AWS" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(any[String])(any[HeaderCarrier])).thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: APIDefinition = someAPIDefinition(versions = Seq(anAPIVersion("1.0"), anAPIVersion("2.0")))

      await(underTest.delete(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--1.0")(hc)
      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--2.0")(hc)
    }

    "return unit if deletion fails" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(any[String])(any[HeaderCarrier])).thenReturn(Future.failed(new RuntimeException()))

      val result: Unit = await(underTest.delete(someAPIDefinition()))

      result shouldBe(())
    }
  }
}
