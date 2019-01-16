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

package uk.gov.hmrc.apidefinition.models

import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models.HttpMethod._
import uk.gov.hmrc.apidefinition.models.WSO2APIDefinition._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class WSO2APIDefinitionSpec extends UnitSpec with MockitoSugar {

  "buildProductionUrl" should {
    trait Setup {
      val apiDefinition = mock[APIDefinition]
      when(apiDefinition.context).thenReturn("calendar")
      when(apiDefinition.serviceBaseUrl).thenReturn("http://localhost:9000")
    }

    "return None if endpoints are not enabled" in new Setup {
      val apiVersion = APIVersion("1.0", APIStatus.BETA, endpoints = Seq.empty, endpointsEnabled = Some(false))
      buildProductionUrl(apiVersion, apiDefinition) shouldBe None
    }

    "return valid URL if endpoints are enabled" in new Setup {
      val apiVersion = APIVersion("1.0", APIStatus.BETA, endpoints = Seq.empty, endpointsEnabled = Some(true))
      buildProductionUrl(apiVersion, apiDefinition) shouldBe Some("http://localhost:9000")
    }
  }

  "buildSandboxUrl" should {

    "return valid URL" in {

      val apiDefinition = mock[APIDefinition]
      when(apiDefinition.context).thenReturn("calendar")
      when(apiDefinition.serviceBaseUrl).thenReturn("http://localhost:9000")

      buildSandboxUrl(apiDefinition) shouldBe "http://localhost:9000/sandbox"

    }

  }

  "wso2ApiName" should {

    "replace '/' in context with '--' to prevent errors in WSO2" in {

      val apiDefinition = mock[APIDefinition]
      when(apiDefinition.context).thenReturn("my/calendar")

      wso2ApiName("1.0", apiDefinition) shouldBe "my--calendar--1.0"
    }

  }

  "EndpointUrl" should {

    case class Scenario(inputUri: String, rootSegments: String, tailSegment: String, tailSegmentIsParameterized: Boolean = false)

    val scenarios = Seq(
      Scenario("", "", ""),
      Scenario("/", "", "/"),
      Scenario("/ni", "", "/ni"),
      Scenario("/ni/{nino}", "/ni", "/{nino}", tailSegmentIsParameterized = true),
      Scenario("/ni/{nino}/periods", "/ni/{nino}", "/periods")
    )

    scenarios.foreach(scenario => {
      s"split the URI segments into ${scenario.rootSegments} and ${scenario.tailSegment} for ${scenario.inputUri}" in {
        val endpoint = EndpointUrl(scenario.inputUri)
        endpoint.rootSegments shouldBe scenario.rootSegments
        endpoint.tailSegment shouldBe scenario.tailSegment
        endpoint.tailSegmentIsParameter shouldBe scenario.tailSegmentIsParameterized
      }
    })
  }

  "wso2ApiStatus" should {
    def mappedStatus(status: APIStatus) = wso2ApiStatus(anApiDefinitionWithVersionAndStatus("1.0", status), aWso2ApiDefinition("1.0"))

    "map ALPHA to PUBLISHED" in {
      mappedStatus(APIStatus.ALPHA) shouldBe "PUBLISHED"
    }
    "map BETA to PUBLISHED" in {
      mappedStatus(APIStatus.BETA) shouldBe "PUBLISHED"
    }
    "map PROTOTYPED to PUBLISHED" in {
      mappedStatus(APIStatus.PROTOTYPED) shouldBe "PUBLISHED"
    }
    "map STABLE to PUBLISHED" in {
      mappedStatus(APIStatus.STABLE) shouldBe "PUBLISHED"
    }
    "map PUBLISHED to PUBLISHED" in {
      mappedStatus(APIStatus.PUBLISHED) shouldBe "PUBLISHED"
    }
    "map DEPRECATED to DEPRECATED" in {
      mappedStatus(APIStatus.DEPRECATED) shouldBe "DEPRECATED"
    }
    "map RETIRED to RETIRED" in {
      mappedStatus(APIStatus.RETIRED) shouldBe "RETIRED"
    }
  }

  "allWSO2SubscriptionTiers" should {

    val expectedAvailableTiers = Seq(
      "BRONZE_SUBSCRIPTION",
      "SILVER_SUBSCRIPTION",
      "GOLD_SUBSCRIPTION",
      "PLATINUM_SUBSCRIPTION")

    "define all available subscription throttling tiers" in {
      WSO2APIDefinition.allAvailableWSO2SubscriptionTiers shouldBe expectedAvailableTiers
    }

  }

  private def anApiDefinitionWithVersionAndStatus(version: String, status: APIStatus) = {
    APIDefinition(
      "calendar",
      "http://localhost:9000",
      "Calendar API",
      "My Calendar API",
      "calendar",
      Seq(
        APIVersion(
          version,
          status,
          Some(PublicAPIAccess()),
          Seq(
            Endpoint(
              "/today",
              "Get Today's Date",
              GET,
              AuthType.USER,
              ResourceThrottlingTier.UNLIMITED,
              Some("read:calendar"))),
          endpointsEnabled = Some(true))),
      None)
  }

  private def aWso2ApiDefinition(version: String) = {
    WSO2APIDefinition(
      s"calendar--$version",
      "{version}/calendar",
      version,
      0,
      WSO2EndpointConfig(
        Some(WSO2Endpoint("http://localhost:9000")),
        WSO2Endpoint("http://localhost:9000/sandbox")),
      None
    )
  }

}
