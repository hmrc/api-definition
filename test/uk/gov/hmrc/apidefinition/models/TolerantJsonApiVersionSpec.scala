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

import play.api.libs.json.OFormat
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class TolerantJsonApiVersionSpec extends BaseJsonFormattersSpec {

  implicit val useMe: OFormat[ApiVersion] = TolerantJsonApiVersion.tolerantFormatApiVersion

  val endpointsText = """ "endpoints": [
                        |  {
                        |    "uriPattern":"/today",
                        |    "endpointName":"Get Today's Date",
                        |    "method":"GET",
                        |    "authType":"NONE",
                        |    "throttlingTier":"UNLIMITED"
                        |  }
                        |]""".stripMargin

  val endpoints = List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED, None, List.empty))

  val baseApiVersion = ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, ApiAccess.PUBLIC, endpoints, true, None, ApiVersionSource.OAS)

  "TolerantJsonApiVersion" should {

    "read api version from Json with all fields" in {
      val json = s"""{
                    |    "version" : "1.0",
                    |    "status" : "STABLE",
                    |    "acccess" : "PUBLIC",
                    |    ${endpointsText},
                    |    "endpointsEnabled": true,
                    |    "awsRequestId": "12345",
                    |    "versionSource": "OAS"
                    |}""".stripMargin

      testFromJson[ApiVersion](json)(baseApiVersion.copy(awsRequestId = Some("12345")))
    }

    "read api version from Json with all fields and newer name of versionNbr" in {
      val json = s"""{
                    |    "versionNbr" : "1.0",
                    |    "status" : "STABLE",
                    |    "acccess" : "PUBLIC",
                    |    ${endpointsText},
                    |    "endpointsEnabled": true,
                    |    "awsRequestId": "12345",
                    |    "versionSource": "OAS"
                    |}""".stripMargin

      testFromJson[ApiVersion](json)(baseApiVersion.copy(awsRequestId = Some("12345")))
    }

    "read endpoint from Json with minimal fields" in {
      val json = s"""{
                    |    "version" : "1.0",
                    |    "status" : "STABLE",
                    |    ${endpointsText}
                    |}""".stripMargin

      testFromJson[ApiVersion](json)(baseApiVersion.copy(versionSource = ApiVersionSource.UNKNOWN))
    }

    "fail to read private access from Json when bad endpoints enabled type" in {
      val ignoreMe = baseApiVersion

      val json = s"""{
                    |    "version" : "1.0",
                    |    "status" : "STABLE",
                    |    "acccess" : "PUBLIC",
                    |    ${endpointsText},
                    |    "endpointsEnabled": "bob"
                    |}""".stripMargin

      intercept[RuntimeException] {
        testFromJson[ApiVersion](json)(ignoreMe)
      }
    }

    "fail to read private access from Json when bad version source type" in {
      val ignoreMe = baseApiVersion

      val json = s"""{
                    |    "version" : "1.0",
                    |    "status" : "STABLE",
                    |    "acccess" : "PUBLIC",
                    |    ${endpointsText},
                    |    "versionSource": 123
                    |}""".stripMargin

      intercept[RuntimeException] {
        testFromJson[ApiVersion](json)(ignoreMe)
      }
    }
  }
}
