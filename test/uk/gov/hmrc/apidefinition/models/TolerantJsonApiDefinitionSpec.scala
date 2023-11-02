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

package uk.gov.hmrc.apidefinition.models

import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class TolerantJsonApiDefinitionSpec extends BaseJsonFormattersSpec {
  implicit val useMe = TolerantJsonApiDefinition.tolerantFormatApiDefinition
  val appId          = ApplicationId.random

  "TolerantJsonApiDefinition" should {
    def anApiDefinition(
        accessType: String = "PUBLIC",
        isTrial: Boolean = false,
        requiresTrust: Option[Boolean] = None,
        isTestSupport: Option[Boolean] = Some(false),
        categories: Option[String] = None
      ) = {

      val isTestSupportText = isTestSupport.fold("")(x => s""" "isTestSupport": $x,""")
      val requiresTrustText = requiresTrust.fold("")(x => s""" "requiresTrust": $x,""")
      val categoriesText    = categories.fold("")(text => s""" "categories": $text,""")

      val body =
        s"""{
           |   "serviceName":"calendar",
           |   "name":"Calendar API",
           |   "description":"My Calendar API",
           |   "serviceBaseUrl":"http://calendar",
           |   "context":"calendar",
           |   $categoriesText
           |   $requiresTrustText
           |   $isTestSupportText
           |   "versions":[
           |      {
           |         "version":"1.0",
           |         "status":"STABLE",
           |         "access": {
           |           "type": "$accessType",
           |           "isTrial": $isTrial
           |         },
           |         "endpoints":[
           |            {
           |               "uriPattern":"/today",
           |               "endpointName":"Get Today's Date",
           |               "method":"GET",
           |               "authType":"NONE",
           |               "throttlingTier":"UNLIMITED"
           |            }
           |         ]
           |      }
           |   ]
           |}""".stripMargin.replaceAll("\n", " ")

      Json.parse(body).as[StoredApiDefinition]
    }

    "read from JSON when the API access type is PUBLIC and there is no whitelist" in {
      val apiDefinition = anApiDefinition()

      apiDefinition.versions.head.access shouldBe ApiAccess.PUBLIC
    }

    "read from JSON when the API access type is PRIVATE and isTrial is true" in {
      val apiDefinition = anApiDefinition(
        accessType = "PRIVATE",
        isTrial = true,
        requiresTrust = Some(true)
      )

      apiDefinition.versions.head.access shouldBe ApiAccess.Private(true)
      apiDefinition.requiresTrust shouldBe true
    }

    "read from JSON when the API categories are defined but empty" in {
      val apiDefinition = anApiDefinition(categories = Some("[]"))

      apiDefinition.categories shouldBe Nil
    }

    "read from JSON when the API categories are missing" in {
      val apiDefinition = anApiDefinition(categories = None)

      apiDefinition.categories shouldBe Nil
    }

    "read from JSON when the API categories are defined with correct values" in {
      val apiDefinition = anApiDefinition(categories = Some("[\"CUSTOMS\", \"VAT\"]"))

      apiDefinition.categories shouldBe Seq(ApiCategory.CUSTOMS, ApiCategory.VAT)
    }

    "read from JSON when there is no isTestSupport" in {
      val apiDefinition = anApiDefinition(isTestSupport = None)

      apiDefinition.isTestSupport shouldBe false
    }

    "fail to read from JSON when the API categories are defined with incorrect values" in {
      intercept[RuntimeException] {
        anApiDefinition(categories = Some("[\"NOT_A_VALID_CATEGORY\"]"))
      }
    }
  }

}
