/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.WSO2APIPublisherConnector
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.services.WSO2APIPublisher
import uk.gov.hmrc.apidefinition.utils.WSO2PayloadHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class WSO2APIPublisherSpec extends UnitSpec
  with ScalaFutures with MockitoSugar {

  private trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val cookie = "login-cookie-123"

    val underTest = new WSO2APIPublisher(mock[AppConfig], mock[WSO2APIPublisherConnector])

    when(underTest.wso2PublisherConnector.login()).thenReturn(successful(cookie))

    when(underTest.appContext.buildProductionUrlForPrototypedAPIs).thenReturn(successful(false))

    implicit val appContext = underTest.appContext
  }

  "createOrUpdate" should {

    "login to WSO2 and create the API when none exists" in new Setup {

      when(underTest.wso2PublisherConnector.doesAPIExist(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(false))
      when(underTest.wso2PublisherConnector.createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))
      when(underTest.wso2PublisherConnector.publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))

      val result = await(underTest.publish(someAPIDefinition))

      verify(underTest.wso2PublisherConnector)
        .createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier]))
      verify(underTest.wso2PublisherConnector)
        .publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier]))
    }

    Seq("sso-in/sso", "web-session/sso-api").foreach { context =>
      s"skip publishing if the context is $context" in new Setup {
        await(underTest.publish(someAPIDefinition.copy(context = context)))

        verify(underTest.wso2PublisherConnector).login()
        verifyNoMoreInteractions(underTest.wso2PublisherConnector)
      }
    }

    "login to WSO2 and update the API when it already exists" in new Setup {

      when(underTest.wso2PublisherConnector.doesAPIExist(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(true))
      when(underTest.wso2PublisherConnector.updateAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))
      when(underTest.wso2PublisherConnector.publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))

      val result = await(underTest.publish(someAPIDefinition))

      verify(underTest.wso2PublisherConnector)
        .updateAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier]))
      verify(underTest.wso2PublisherConnector)
        .publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier]))
    }

    "fail when an update to WSO2 responds with an error" in new Setup {

      when(underTest.wso2PublisherConnector.doesAPIExist(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(false))
      when(underTest.wso2PublisherConnector.createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))
      when(underTest.wso2PublisherConnector.publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier])))
        .thenReturn(failed(new RuntimeException("Something went wrong")))

      underTest.publish(someAPIDefinition).map{
        result => fail("Exception was expected but not thrown")
      }.recover {
        case _ => ()
      }

    }

  }

  "hasSubscribers" should {

    "return true when the API has subscribers" in new Setup {

      val wso2APIDefinition = WSO2PayloadHelper.buildWSO2APIDefinitions(someAPIDefinition).head.copy(subscribersCount = 4)

      given(underTest.wso2PublisherConnector.fetchAPI(cookie, "calendar--1.0", "1.0"))
        .willReturn(successful(wso2APIDefinition))

      await(underTest.hasSubscribers(someAPIDefinition)) shouldEqual true
    }

    "return false when the API does not have subscribers" in new Setup {

      val wso2APIDefinition = WSO2PayloadHelper.buildWSO2APIDefinitions(someAPIDefinition).head.copy(subscribersCount = 0)

      given(underTest.wso2PublisherConnector.fetchAPI(cookie, "calendar--1.0", "1.0"))
        .willReturn(successful(wso2APIDefinition))

      await(underTest.hasSubscribers(someAPIDefinition)) shouldEqual false
    }
  }

  "publishAll" should {

    "publish all APIs" in new Setup {

      val apiDefinition1 = someAPIDefinition
      val apiDefinition2 = someAPIDefinition

      when(underTest.wso2PublisherConnector.doesAPIExist(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(false))
      when(underTest.wso2PublisherConnector.createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))
      when(underTest.wso2PublisherConnector.publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))

      val result = await(underTest.publish(Seq(apiDefinition1, apiDefinition2)))

      result.isEmpty shouldBe true

      verify(underTest.wso2PublisherConnector, times(2))
        .doesAPIExist(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier]))
      verify(underTest.wso2PublisherConnector, times(2))
        .createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier]))
      verify(underTest.wso2PublisherConnector, times(2))
        .publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier]))
    }

    "attempt to publish all APIs and return the list of APIs that failed to publish" in new Setup {

      val exception = new RuntimeException("Some error occurred")

      when(underTest.wso2PublisherConnector.doesAPIExist(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(false))
      when(underTest.wso2PublisherConnector.createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))
      when(underTest.wso2PublisherConnector.publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier])))
        .thenReturn(failed(exception), failed(exception), successful(()))

      val result = await(underTest.publish(Seq(someAPIDefinition, someAPIDefinition, someAPIDefinition)))

      result shouldBe Seq(someAPIDefinition.name, someAPIDefinition.name)
    }
  }

  "delete" should {

    "login to WSO2 and remove all expected APIs" in new Setup {

      given(underTest.wso2PublisherConnector.removeAPI(cookie, "calendar--1.0", "1.0"))
        .willReturn(successful(()))
      given(underTest.wso2PublisherConnector.removeAPI(cookie, "calendar--2.0", "2.0"))
        .willReturn(successful(()))
      given(underTest.wso2PublisherConnector.removeAPI(cookie, "calendar--3.0", "3.0"))
        .willReturn(successful(()))

      val apiDefinition = someAPIDefinition.copy(
        versions = Seq(
          someAPIVersion,
          someAPIVersion.copy(version = "2.0"),
          someAPIVersion.copy(version = "3.0")
        )
      )

      await(underTest.delete(apiDefinition)) shouldEqual ((): Unit)

      verify(underTest.wso2PublisherConnector, times(3))
        .removeAPI(refEq(cookie), any(classOf[String]), any(classOf[String]))(any(classOf[HeaderCarrier]))
    }

    Seq("sso-in/sso", "web-session/sso-api").foreach { context =>
      s"skip deletion if the context is $context" in new Setup {
        await(underTest.delete(someAPIDefinition.copy(context = context)))

        verifyZeroInteractions(underTest.wso2PublisherConnector)
      }
    }
  }

  private def someAPIVersion = {
      APIVersion(
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
  }

  private def someAPIDefinition = {
    APIDefinition(
      "calendar",
      "http://calendar",
      "Calendar API",
      "My Calendar API",
      "calendar",
      Seq(someAPIVersion),
      None)
  }
}
