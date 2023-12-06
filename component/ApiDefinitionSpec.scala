/*
 * Copyright 2021 HM Revenue & Customs
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

package component

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{NO_CONTENT, OK}

class ApiDefinitionSpec extends ComponentSpec{

  val validDefinition1AsString = """{"serviceName":"calendar","serviceBaseUrl":"http://calendar","name":"Calendar API","description":"My Calendar API","context":"individuals/calendar","versions":[{"version":"1.0","status":"STABLE","access":{"type":"PUBLIC"},"endpoints":[{"uriPattern":"/today","endpointName":"Get Today's Date","method":"GET","authType":"NONE","throttlingTier":"UNLIMITED","queryParameters":[]}],"endpointsEnabled":true,"versionSource":"UNKNOWN"}],"requiresTrust":true,"isTestSupport":false,"categories":["OTHER"]}"""
  val validDefinition2AsString = """{"serviceName":"calendar","serviceBaseUrl":"http://calendar","name":"Calendar API","description":"My Calendar API","context":"individuals/calendar","versions":[{"version":"1.0","status":"PUBLISHED","endpoints":[{"uriPattern":"/today","endpointName":"Get Today's Date","method":"GET","authType":"NONE","throttlingTier":"UNLIMITED"}]}],"isTestSupport":false,"categories":["OTHER"]}"""
  
  val validHeaders = List(CONTENT_TYPE -> "application/json")

  def stubAWSGateway(): Unit = {
    val expectedRequestId = UUID.randomUUID.toString
    
    stubFor(
      WireMock.put(urlPathEqualTo("/v1/api/individuals--calendar--1.0"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(CONTENT_TYPE, "application/json")
            .withBody(s"""{ "RequestId" : "$expectedRequestId" }""")
        )
    )
  }

  "GET /api-definition" should {
    "respond OK" in {
      val response = get("/api-definition")
      response.status shouldBe OK
    }
  }

  "POST /api-definition" should {
    "return No Content for valid Json payload" in {
      stubAWSGateway()
     val response =  post("/api-definition", validDefinition1AsString, validHeaders)
     response.status shouldBe NO_CONTENT
    }

    "return No Content and be tolerant for json missing values" in {
      stubAWSGateway()
     val response =  post("/api-definition", validDefinition2AsString, validHeaders)

     response.status shouldBe NO_CONTENT
    }
  }
}
