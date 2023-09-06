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

import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiAccess

class APIDefinitionSpec extends AsyncHmrcSpec {

  "APIDefinition" should {
    def anApiDefinition(accessType: String = "PUBLIC", whitelistedApplicationIds: Option[String] = None, isTrial: Option[Boolean] = None, categories: Option[String] = None) = {

      val body =
        s"""{
           |   "serviceName":"calendar",
           |   "name":"Calendar API",
           |   "description":"My Calendar API",
           |   "serviceBaseUrl":"http://calendar",
           |   "context":"calendar",
           |   ${categories.fold("")(c => s""" "categories": $c,""")}
           |   "versions":[
           |      {
           |         "version":"1.0",
           |         "status":"PUBLISHED",
           |         "access": {
           |           "type": "$accessType"
           |            ${whitelistedApplicationIds.fold("")(w => s""" ,"whitelistedApplicationIds": $w""")}
           |            ${isTrial.fold("")(t => s""","isTrial": $t""")}
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

      Json.parse(body).as[APIDefinition]
    }

    "read from JSON when the API access type is PUBLIC and there is no whitelist" in {
      val apiDefinition = anApiDefinition()

      apiDefinition.versions.head.access shouldBe Some(ApiAccess.PUBLIC)
    }

    "read from JSON when the API access type is PUBLIC and there is an empty whitelist" in {
      val apiDefinition = anApiDefinition(whitelistedApplicationIds = Some("[]"))

      apiDefinition.versions.head.access shouldBe Some(ApiAccess.PUBLIC)
    }

    // This no longer applies - we just ignore the whitelisted app ids like we do for all other 'odd' data
    // "fail to read from JSON when the API access type is PUBLIC and there is a non-empty whitelist" in {
    //   intercept[RuntimeException] {
    //     anApiDefinition(whitelistedApplicationIds = Some("[\"an-application-id\"]"))
    //   }
    // }

    "read from JSON when the API access type is PRIVATE and there is an empty whitelist" in {
      val apiDefinition = anApiDefinition(
        accessType = "PRIVATE",
        whitelistedApplicationIds = Some("[]")
      )

      apiDefinition.versions.head.access shouldBe Some(ApiAccess.Private(Nil))
    }

    "read from JSON when the API access type is PRIVATE and there is an empty whitelist and isTrial is true" in {
      val apiDefinition = anApiDefinition(
        accessType = "PRIVATE",
        whitelistedApplicationIds = Some("[]"),
        isTrial = Some(true)
      )

      apiDefinition.versions.head.access shouldBe Some(ApiAccess.Private(Nil, Some(true)))
    }

    "read from JSON when the API access type is PRIVATE and there is a non-empty whitelist" in {
      val apiDefinition = anApiDefinition(accessType = "PRIVATE", whitelistedApplicationIds = Some("[\"an-application-id\"]"))

      apiDefinition.versions.head.access shouldBe Some(ApiAccess.Private(List("an-application-id")))
    }

    "no longer fail to read from JSON when the API access type is PRIVATE and there is no whitelist" in {
      val apiDefinition = anApiDefinition(accessType = "PRIVATE", whitelistedApplicationIds = None)

      apiDefinition.versions.head.access shouldBe Some(ApiAccess.Private(List.empty))
    }

    "read from JSON when the API categories are defined but empty" in {
      val apiDefinition = anApiDefinition(categories = Some("[]"))

      apiDefinition.categories shouldBe Some(Nil)
    }

    "read from JSON when the API categories are defined with correct values" in {
      val apiDefinition = anApiDefinition(categories = Some("[\"CUSTOMS\", \"VAT\"]"))

      apiDefinition.categories shouldBe Some(Seq(ApiCategory.CUSTOMS, ApiCategory.VAT))
    }

    "fail to read from JSON when the API categories are defined with incorrect values" in {
      intercept[RuntimeException] {
        anApiDefinition(categories = Some("[\"NOT_A_VALID_CATEGORY\"]"))
      }
    }
  }

  "ApiCategory" should {
    "return details for a given category" in {
      val details = ApiCategory.toApiCategoryDetails(ApiCategory.BUSINESS_RATES)

      details.category shouldBe ApiCategory.BUSINESS_RATES
      details.name shouldBe "Business Rates"
    }

    "return appropriate ApiCategoryDetails objects for each ApiCategory" in {
      ApiCategory.values.foreach { category =>
        val details = ApiCategory.toApiCategoryDetails(category)
        details.category shouldBe category
      }
    }
  }
}
