/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.play.test.UnitSpec

class APIDefinitionSpec extends UnitSpec {

  "APIDefinition" should {

    "read from JSON when the API access type is PUBLIC and there is no whitelist" in {
      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"PUBLISHED",
          |         "access": {
          |           "type": "PUBLIC"
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

      val apiDefinition = Json.parse(body).as[APIDefinition]
      apiDefinition.versions.head.access shouldBe Some(PublicAPIAccess())
    }

    "read from JSON when the API access type is PUBLIC and there is an empty whitelist" in {
      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"PUBLISHED",
          |         "access": {
          |           "type": "PUBLIC",
          |           "whitelistedApplicationIds" : []
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

      val apiDefinition = Json.parse(body).as[APIDefinition]
      apiDefinition.versions.head.access shouldBe Some(PublicAPIAccess())
    }

    "fail to read from JSON when the API access type is PUBLIC and there is a non-empty whitelist" in {
      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"PUBLISHED",
          |         "access": {
          |           "type": "PUBLIC",
          |           "whitelistedApplicationIds" : [ "an-application-id" ]
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

      intercept[RuntimeException] { Json.parse(body).as[APIDefinition] }
    }

    "read from JSON when the API access type is PRIVATE and there is an empty whitelist" in {
      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"PUBLISHED",
          |         "access": {
          |           "type": "PRIVATE",
          |           "whitelistedApplicationIds" : []
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

      val apiDefinition = Json.parse(body).as[APIDefinition]
      apiDefinition.versions.head.access shouldBe Some(PrivateAPIAccess(Seq.empty))
    }

    "read from JSON when the API access type is PRIVATE and there is a non-empty whitelist" in {
      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"PUBLISHED",
          |         "access": {
          |           "type": "PRIVATE",
          |           "whitelistedApplicationIds" : [ "an-application-id" ]
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

      val apiDefinition = Json.parse(body).as[APIDefinition]
      apiDefinition.versions.head.access shouldBe Some(PrivateAPIAccess(Seq("an-application-id")))
    }

    "fail to read from JSON when the API access type is PRIVATE and there is no whitelist" in {
      val body =
        """{
          |   "serviceName":"calendar",
          |   "name":"Calendar API",
          |   "description":"My Calendar API",
          |   "serviceBaseUrl":"http://calendar",
          |   "context":"calendar",
          |   "versions":[
          |      {
          |         "version":"1.0",
          |         "status":"PUBLISHED",
          |         "access": {
          |           "type": "PRIVATE"
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

      intercept[RuntimeException] { Json.parse(body).as[APIDefinition] }
    }

  }

}
