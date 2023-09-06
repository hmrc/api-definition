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

package uk.gov.hmrc.apiplatform.modules.apis.domain.models

import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec
import play.api.libs.json._

class ApiCategoryDetailsSpec extends BaseJsonFormattersSpec {
  
  "ApiCategoryDetails" should {
    val details = ApiCategoryDetails.toApiCategoryDetails(ApiCategory.BUSINESS_RATES)
    
    "return details for a given category" in {

      details.category shouldBe ApiCategory.BUSINESS_RATES
      details.name shouldBe "Business Rates"
    }

    "return appropriate ApiCategoryDetails objects for each ApiCategory" in {
      ApiCategory.values.foreach { category =>
        val details = ApiCategoryDetails.toApiCategoryDetails(category)
        details.category shouldBe category
      }
    }

    "read from Json" in {
      testFromJson[ApiCategoryDetails]("""{"category":"BUSINESS_RATES","name":"Business Rates"}""")(details)
    }

    "write to Json" in {
      Json.toJson[ApiCategoryDetails](details) shouldBe Json.obj(
        "category" -> "BUSINESS_RATES",
        "name" -> "Business Rates"
      )
    }    
  }
}
