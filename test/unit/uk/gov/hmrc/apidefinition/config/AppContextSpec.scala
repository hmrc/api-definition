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

package unit.uk.gov.hmrc.apidefinition.config

import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers}
import org.scalatestplus.play.OneAppPerTest
import play.api.Configuration
import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.play.test.UnitSpec

class AppContextSpec extends UnitSpec
  with Matchers  with MockitoSugar
  with BeforeAndAfterEach with OneAppPerTest {

  trait Setup {
    val mockConfig =  mock[Configuration]
    val underTest = new AppContext(mockConfig)

    def whenTestEnvironmentUndefined = when(mockConfig.getBoolean("buildProductionUrlForPrototypedAPIs")).thenReturn(None)
    def whenTestEnvironmentEnabled = when(mockConfig.getBoolean("buildProductionUrlForPrototypedAPIs")).thenReturn(Some(true))
    def whenTestEnvironmentDisable = when(mockConfig.getBoolean("buildProductionUrlForPrototypedAPIs")).thenReturn(Some(false))
  }

  "App Context" should {

    "build flag defaults to false when no config provided" in new Setup {
      whenTestEnvironmentUndefined
      underTest.buildProductionUrlForPrototypedAPIs shouldBe false
    }

    "build flag set to true when config provides boolean value" in new Setup {
      whenTestEnvironmentEnabled
      underTest.buildProductionUrlForPrototypedAPIs shouldBe true
    }

    "build flag set to false when config provides boolean value" in new Setup {
      whenTestEnvironmentDisable
      underTest.buildProductionUrlForPrototypedAPIs shouldBe false
    }

  }

}
