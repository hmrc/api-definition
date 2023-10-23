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
import scala.concurrent.Future.{failed, successful}
import scala.jdk.CollectionConverters._

import org.mockito.{ArgumentCaptor, ArgumentMatchers}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{StoredApiDefinition, ApiStatus, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.AwsApiPublisher
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class AwsAPIPublisherSpec extends AsyncHmrcSpec {

  private def anAPIVersion(version: String, status: ApiStatus = ApiStatus.BETA, queryParams: List[QueryParameter] = Nil) = ApiVersion(
    ApiVersionNbr(version),
    status,
    ApiAccess.PUBLIC,
    List(
      Endpoint(
        "/today/{id}",
        "Get Today's Date",
        HttpMethod.GET,
        AuthType.NONE,
        ResourceThrottlingTier.UNLIMITED,
        queryParameters = queryParams
      )
    )
  )

  private val host = UUID.randomUUID().toString

  private def someAPIDefinition(
      name: String = UUID.randomUUID().toString,
      context: ApiContext = ApiContext.random,
      serviceBaseUrl: String = s"https://$host",
      versions: List[ApiVersion] = List(anAPIVersion("2.0"))
    ): StoredApiDefinition = {
    StoredApiDefinition(
      ServiceName(UUID.randomUUID().toString),
      serviceBaseUrl,
      name,
      UUID.randomUUID().toString,
      context,
      versions,
      false,
      false,
      None,
      List(ApiCategory.OTHER)
    )
  }

  private trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new AwsApiPublisher(mock[AWSAPIPublisherConnector], mock[APIDefinitionRepository])
    when(underTest.apiDefinitionRepository.fetchByServiceName(*[ServiceName])).thenReturn(successful(None))
  }

  "publishAll" should {
    "create or update all the APIs in AWS" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(*, swaggerDetailsCaptor.capture())(*))
        .thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition1: StoredApiDefinition                           = someAPIDefinition("API 1")
      val apiDefinition2: StoredApiDefinition                           = someAPIDefinition("API 2")

      await(underTest.publishAll(List(apiDefinition1, apiDefinition2)))

      verify(underTest.awsAPIPublisherConnector, times(2)).createOrUpdateAPI(*, *[AWSSwaggerDetails])(*)
      val swaggerDetails: Seq[AWSSwaggerDetails] = swaggerDetailsCaptor.getAllValues.asScala.toSeq
      swaggerDetails.head.info.title shouldBe apiDefinition1.name
      swaggerDetails(1).info.title shouldBe apiDefinition2.name
    }
  }

  "publish" should {
    "create or update the API in AWS" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(*, swaggerDetailsCaptor.capture())(*))
        .thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: StoredApiDefinition                            = someAPIDefinition()

      await(underTest.publish(apiDefinition))

      verify(underTest.awsAPIPublisherConnector)
        .createOrUpdateAPI(ArgumentMatchers.eq(s"${apiDefinition.context}--2.0"), *)(*)
      val swaggerDetails: AWSSwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some(host)
      swaggerDetails.info.title shouldBe apiDefinition.name
      swaggerDetails.paths.head._2.apply("get").parameters shouldBe Some(List(AWSPathParameter("id")))
    }

    "create or update the API in AWS with path and query params" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(*, swaggerDetailsCaptor.capture())(*))
        .thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: StoredApiDefinition                            = someAPIDefinition(versions = List(anAPIVersion("2.0", ApiStatus.STABLE, List(QueryParameter("flag",true)))))

      await(underTest.publish(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).createOrUpdateAPI(ArgumentMatchers.eq(s"${apiDefinition.context}--2.0"), *)(*)
      val swaggerDetails: AWSSwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some(host)
      swaggerDetails.info.title shouldBe apiDefinition.name
      swaggerDetails.paths.head._2.apply("get").parameters shouldBe Some((List(AWSPathParameter("id", true, "string"), AWSQueryParameter("flag", true, "string"))))
    }

    "populate correctly the host from service base URLs using HTTP" in new Setup {
      val swaggerDetailsCaptor: ArgumentCaptor[AWSSwaggerDetails] = ArgumentCaptor.forClass(classOf[AWSSwaggerDetails])
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(*, swaggerDetailsCaptor.capture())(*))
        .thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.publish(someAPIDefinition(serviceBaseUrl = s"http://$host")))

      val swaggerDetails: AWSSwaggerDetails = swaggerDetailsCaptor.getValue
      swaggerDetails.host shouldBe Some(host)
    }

    "call delete endpoint for RETIRED versions" in new Setup {
      val apiDefinition: StoredApiDefinition = someAPIDefinition(versions = List(anAPIVersion("1.0", ApiStatus.RETIRED), anAPIVersion("2.0")))

      when(underTest.awsAPIPublisherConnector.deleteAPI(*)(*))
        .thenReturn(successful(UUID.randomUUID().toString))
      when(underTest.awsAPIPublisherConnector.createOrUpdateAPI(*, *)(*))
        .thenReturn(successful(UUID.randomUUID().toString))

      await(underTest.publish(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--1.0")(hc)
      verify(underTest.awsAPIPublisherConnector)
        .createOrUpdateAPI(ArgumentMatchers.eq(s"${apiDefinition.context}--2.0"), *)(*)
    }
  }

  "delete" should {
    "delete the API in AWS" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(*)(*)).thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: StoredApiDefinition = someAPIDefinition()

      await(underTest.delete(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--2.0")(hc)
    }

    "delete multiple versions of the API in AWS" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(*)(*)).thenReturn(successful(UUID.randomUUID().toString))
      val apiDefinition: StoredApiDefinition = someAPIDefinition(versions = List(anAPIVersion("1.0"), anAPIVersion("2.0")))

      await(underTest.delete(apiDefinition))

      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--1.0")(hc)
      verify(underTest.awsAPIPublisherConnector).deleteAPI(s"${apiDefinition.context}--2.0")(hc)
    }

    "return unit if deletion fails" in new Setup {
      when(underTest.awsAPIPublisherConnector.deleteAPI(*)(*)).thenReturn(failed(new RuntimeException()))

      val result: Unit = await(underTest.delete(someAPIDefinition()))

      result shouldBe (())
    }
  }
}
