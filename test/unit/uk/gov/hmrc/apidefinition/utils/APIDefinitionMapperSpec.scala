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

package unit.uk.gov.hmrc.apidefinition.utils

import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.when
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper
import uk.gov.hmrc.play.test.UnitSpec

class APIDefinitionMapperSpec extends UnitSpec with MockitoSugar {

  private def version(version: String, status: APIStatus, endpointsEnabled: Option[Boolean] = None) = {
    APIVersion(version, status, None,
      Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
      endpointsEnabled = endpointsEnabled)
  }

  private def definition(versions: Seq[APIVersion]) = {
    APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar", versions, None)
  }

  private def underTest(enabledPrototypedEndpoints: Boolean = false) = {
    val appContext = mock[AppContext]
    when(appContext.buildProductionUrlForPrototypedAPIs).thenReturn(enabledPrototypedEndpoints)

    new APIDefinitionMapper(appContext)
  }

  "Mapper" should {

    "map PROTOTYPED to BETA and set endpointsEnabled=true when production URLs for prototyped APIs is enabled" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PROTOTYPED)))
      val mappedDefinition = underTest(true).mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.BETA
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(true)
    }

    "map PROTOTYPED to BETA and set endpointsEnabled=false when production URLs for prototyped APIs is not enabled" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PROTOTYPED)))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.BETA
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    "map PROTOTYPED to BETA and keep value for endpointsEnabled when production URLs for prototyped APIs is enabled" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PROTOTYPED, Some(false))))
      val mappedDefinition = underTest(true).mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.BETA
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    "map PROTOTYPED to BETA and keep value for endpointsEnabled when production URLs for prototyped APIs is not enabled" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PROTOTYPED, Some(true))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.BETA
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(true)
    }

    // PUBLISHED
    "map PUBLISHED to STABLE and set endpointsEnabled=true when not explicitly set" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PUBLISHED)))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.STABLE
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(true)
    }

    "map PUBLISHED to STABLE and preserve endpointsEnabled=false when explicitly set" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PUBLISHED, Some(false))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.STABLE
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    "map PUBLISHED to STABLE and preserve endpointsEnabled=true when explicitly set" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.PUBLISHED, Some(false))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.STABLE
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    // DEPRECATED
    "map DEPRECATED to DEPRECATED and set endpointsEnabled=true when not explicitly set" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.DEPRECATED)))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.DEPRECATED
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(true)
    }

    "map DEPRECATED to DEPRECATED and set endpointsEnabled=false when explicitly set to false" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.DEPRECATED, Some(false))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.DEPRECATED
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    "map DEPRECATED to DEPRECATED and set endpointsEnabled=true when explicitly set to true" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.DEPRECATED, Some(false))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.DEPRECATED
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    // RETIRED
    "map RETIRED to RETIRED and set endpointsEnabled=true when not explicitly set" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.RETIRED)))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.RETIRED
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(true)
    }

    "map RETIRED to RETIRED and set endpointsEnabled=false when explicitly set to false" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.RETIRED, Some(false))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.RETIRED
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }

    "map RETIRED to RETIRED and set endpointsEnabled=true when explicitly set to true" in {
      val originalDefinition = definition(Seq(version("1.0", APIStatus.RETIRED, Some(false))))
      val mappedDefinition = underTest().mapLegacyStatuses(originalDefinition)

      mappedDefinition.versions.head.status shouldBe APIStatus.RETIRED
      mappedDefinition.versions.head.endpointsEnabled shouldBe Some(false)
    }
  }

}
