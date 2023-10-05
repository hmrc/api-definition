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

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import play.api.libs.json.Json
import uk.gov.hmrc.apidefinition.utils.AWSPayloadHelper
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext
import uk.gov.hmrc.apidefinition.models.JsonFormatters._

class APIDefinitionToAWSJsonSpec extends AsyncHmrcSpec {
  
  private def anAPIVersion(version: String, status: ApiStatus = ApiStatus.STABLE, queryParams: List[QueryParameter] = Nil) = ApiVersion(
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

  trait Setup {
  
  }

  "Api Definition to Aws Json" should {

    "write to Json Correctly" in new Setup {
      val version = anAPIVersion("1.0")
      val swaggerDetails = AWSPayloadHelper.buildAWSSwaggerDetails("anApi", version, ApiContext("/my-path"), "host")
      println(Json.prettyPrint(Json.toJson(swaggerDetails)))
    }
  }
}
