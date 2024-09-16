/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.models

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccess, ApiStatus, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}

import uk.gov.hmrc.apidefinition.models.ApiEventFormatter._

class ApiEventSpec extends HmrcSpec with FixedClock {

  val eventId        = EventId.random
  val apiName        = "Api 123"
  val serviceName    = ServiceName("api-123")
  val apiStatus      = ApiStatus.ALPHA
  val otherApiStatus = ApiStatus.BETA
  val apiVersion     = ApiVersionNbr("1.0")
  val apiAccess      = ApiAccess.PUBLIC
  val otherApiAccess = ApiAccess.Private(true)

  "ApiEvent" when {
    "ApiCreated" should {
      val event: ApiEvent = ApiEvents.ApiCreated(eventId, apiName, serviceName, instant)
      val json            = s"{\"id\":\"${eventId.value}\",\"apiName\":\"$apiName\",\"serviceName\":\"$serviceName\",\"eventDateTime\":\"$nowAsText\",\"eventType\":\"API_CREATED\"}"
      "Serialise to valid JSON" in {
        Json.toJson(event).toString shouldBe json
      }

      "Deserialise from json" in {
        Json.parse(json).as[ApiEvent] shouldBe event
      }

      "Have metadata" in {
        event.asMetaData() shouldBe ("Api Created", List.empty)
      }
    }

    "NewApiVersion" should {
      val event: ApiEvent = ApiEvents.NewApiVersion(eventId, apiName, serviceName, instant, apiStatus, apiVersion)
      val json            =
        s"{\"id\":\"${eventId.value}\",\"apiName\":\"$apiName\",\"serviceName\":\"$serviceName\",\"eventDateTime\":\"$nowAsText\",\"apiStatus\":\"ALPHA\",\"versionNbr\":\"1.0\",\"eventType\":\"NEW_API_VERSION\"}"
      "Serialise to valid JSON" in {
        Json.toJson(event).toString shouldBe json
      }

      "Deserialise from json" in {
        Json.parse(json).as[ApiEvent] shouldBe event
      }

      "Have metadata" in {
        event.asMetaData() shouldBe ("New Api Version Published", List("Version: 1.0", "Api Status: Alpha"))
      }
    }

    "ApiVersionStatusChange" should {
      val event: ApiEvent = ApiEvents.ApiVersionStatusChange(eventId, apiName, serviceName, instant, apiStatus, otherApiStatus, apiVersion)
      val json            =
        s"{\"id\":\"${eventId.value}\",\"apiName\":\"$apiName\",\"serviceName\":\"$serviceName\",\"eventDateTime\":\"$nowAsText\",\"oldApiStatus\":\"ALPHA\",\"newApiStatus\":\"BETA\",\"versionNbr\":\"1.0\",\"eventType\":\"API_VERSION_STATUS_CHANGE\"}"
      "Serialise to valid JSON" in {
        Json.toJson(event).toString shouldBe json
      }

      "Deserialise from json" in {
        Json.parse(json).as[ApiEvent] shouldBe event
      }

      "Have metadata" in {
        event.asMetaData() shouldBe ("Api Version Status Change", List("Version: 1.0", "Old Api Status: Alpha", "New Api Status: Beta"))
      }
    }

    "ApiVersionAccessChange" should {
      val event: ApiEvent = ApiEvents.ApiVersionAccessChange(eventId, apiName, serviceName, instant, apiAccess, otherApiAccess, apiVersion)
      val json            =
        s"{\"id\":\"${eventId.value}\",\"apiName\":\"$apiName\",\"serviceName\":\"$serviceName\",\"eventDateTime\":\"$nowAsText\",\"oldApiAccess\":{\"type\":\"PUBLIC\"},\"newApiAccess\":{\"isTrial\":true,\"type\":\"PRIVATE\"},\"versionNbr\":\"1.0\",\"eventType\":\"API_VERSION_ACCESS_CHANGE\"}"
      "Serialise to valid JSON" in {
        Json.toJson(event).toString shouldBe json
      }

      "Deserialise from json" in {
        Json.parse(json).as[ApiEvent] shouldBe event
      }

      "Have metadata" in {
        event.asMetaData() shouldBe ("Api Version Access Change", List("Version: 1.0", "Old Api Access: Public", "New Api Access: Private Trial"))
      }
    }

    "ApiPublishedNoChange" should {
      val event: ApiEvent = ApiEvents.ApiPublishedNoChange(eventId, apiName, serviceName, instant)
      val json            =
        s"{\"id\":\"${eventId.value}\",\"apiName\":\"$apiName\",\"serviceName\":\"$serviceName\",\"eventDateTime\":\"$nowAsText\",\"eventType\":\"API_PUBLISHED_NO_CHANGE\"}"
      "Serialise to valid JSON" in {
        Json.toJson(event).toString shouldBe json
      }

      "Deserialise from json" in {
        Json.parse(json).as[ApiEvent] shouldBe event
      }

      "Have metadata" in {
        event.asMetaData() shouldBe ("Api Published No Change", List.empty)
      }
    }

  }
}
