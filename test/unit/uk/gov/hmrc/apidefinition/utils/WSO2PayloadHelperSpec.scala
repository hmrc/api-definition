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

import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import play.api.libs.json.Json
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.apidefinition.utils.WSO2PayloadHelper._

import scala.util.Random

class WSO2PayloadHelperSpec extends UnitSpec {

  private val endpoint = Endpoint(
    uriPattern = "",
    endpointName = "",
    method = HttpMethod.GET,
    authType = AuthType.NONE,
    throttlingTier = ResourceThrottlingTier.UNLIMITED,
    scope = None,
    queryParameters = None)

  private val endpointsWithScopes = Seq(
    endpoint,
    endpoint.copy(scope = Some("my-scope")),
    endpoint.copy(scope = Some("a-scope")),
    endpoint.copy(scope = Some("great-scope")),
    endpoint.copy(scope = Some("a-scope")))

  private val queryParameters = Seq(
    Parameter(name = "city"),
    Parameter(name = "address", required = true),
    Parameter(name = "postcode", required = true)
  )

  private val endpointWithQueryParameters = endpoint.copy(queryParameters = Some(queryParameters))

  private val endpointWithPathParameters = endpoint.copy(uriPattern = "/hello/{surname}/{nickname}")

  "buildWSO2Security()" should {

    "be empty if there are no scopes in the endpoints" in {
      buildWSO2Security(endpoints = Seq(endpoint)) shouldBe None
    }

    "contain all (distinct) scopes of the endpoints, sorted by `key` " in {
      val wso2Scope1 = WSO2SwaggerScope(name = "a-scope", key = "a-scope")
      val wso2Scope2 = WSO2SwaggerScope(name = "great-scope", key = "great-scope")
      val wso2Scope3 = WSO2SwaggerScope(name = "my-scope", key = "my-scope")

      val sortedWso2Scopes = Seq(wso2Scope1, wso2Scope2, wso2Scope3)

      val expectedResult = Some(Map("apim" -> Map("x-wso2-scopes" -> sortedWso2Scopes)))

      buildWSO2Security(endpoints = endpointsWithScopes) shouldBe expectedResult
    }
  }

  "buildWSO2PathParameters()" should {

    "return an empty sequence if there are no path parameters" in {
      buildWSO2PathParameters(endpoint.copy(uriPattern = "/hello/world")) shouldBe Seq()
    }

    "return all path parameters sorted by segment precedence" in {
      val expectedPathParameters = Seq(
        WSO2PathParameter(name = "surname"),
        WSO2PathParameter(name = "nickname"))

      buildWSO2PathParameters(endpointWithPathParameters) shouldBe expectedPathParameters
    }
  }

  "buildWSO2QueryParameters()" should {

    "return an empty sequence if there are no query parameters" in {
      buildWSO2QueryParameters(endpoint) shouldBe Seq()
    }

    "return all query parameters sorted by name" in {
      val expectedQueryParameters = Seq(
        WSO2QueryParameter(name = "address", required = true),
        WSO2QueryParameter(name = "city", required = false),
        WSO2QueryParameter(name = "postcode", required = true))

      buildWSO2QueryParameters(endpointWithQueryParameters) shouldBe expectedQueryParameters
    }
  }

  "buildWSO2Parameters()" should {

    "return None if there are no query parameters, nor path parameters" in {
      buildWSO2Parameters(endpoint) shouldBe None
    }

    "return path parameters first and then the query parameters sorted alphabetically" in {
      val endpointWithManyParams = endpointWithQueryParameters.copy(uriPattern = "/hello/{surname}/{nickname}")

      val expectedParameters = Seq(
        WSO2PathParameter(name = "surname"),
        WSO2PathParameter(name = "nickname"),
        WSO2QueryParameter(name = "address", required = true),
        WSO2QueryParameter(name = "city", required = false),
        WSO2QueryParameter(name = "postcode", required = true))

      buildWSO2Parameters(endpointWithManyParams) shouldBe Some(expectedParameters)
    }
  }

  "buildWSO2APIDefinitions()" should {

    "convert the API definition as expected" in {
      val endpoint1 = Endpoint(
        uriPattern = "/friend",
        endpointName = "welcome my friend",
        method = HttpMethod.GET,
        authType = AuthType.NONE,
        throttlingTier = ResourceThrottlingTier.UNLIMITED,
        scope = None,
        queryParameters = None
      )
      val endpoint2 = endpoint1.copy(
        uriPattern = "/application",
        endpointName = "welcome my application",
        authType = AuthType.APPLICATION
      )
      val endpoint3 = endpoint1.copy(
        uriPattern = "/user",
        endpointName = "welcome my user",
        authType = AuthType.USER,
        scope = Some("read:welcome"),
        queryParameters = Some(Seq(
          Parameter(name = "forename", required = true),
          Parameter(name = "surname")
        )))
      val endpoint4 = endpoint3.copy(
        uriPattern = "/user/{friend}",
        endpointName = "RIP my special user",
        method = HttpMethod.DELETE,
        authType = AuthType.USER,
        scope = Some("delete:welcome")
      )
      val endpoint5 = endpoint4.copy(
        endpointName = "GET my special user",
        method = HttpMethod.GET,
        scope = Some("read:welcome")
      )
      val endpoint6 = endpoint3.copy(
        uriPattern = "/user/employee",
        endpointName = "welcome employee",
        method = HttpMethod.POST,
        queryParameters = None
      )

      // shuffling the API definition resources deliberately, as they will be sorted alphabetically
      val shuffledResources = Random.shuffle(Seq(endpoint1, endpoint2, endpoint3, endpoint4, endpoint5, endpoint6))

      val apiVersion = APIVersion(
        version = "1.0",
        status = APIStatus.PUBLISHED,
        access = Some(PublicAPIAccess()),
        endpoints = shuffledResources,
        endpointsEnabled = Some(true)
      )

      val apiDefinition = APIDefinition(
        serviceName = "welcome-api",
        serviceBaseUrl = "http://localhost:9000/service",
        name = "Welcome API",
        description = "This is the welcome API",
        context = "welcome",
        versions = Seq(apiVersion),
        requiresTrust = None)

      val wso2ApiDefinition = buildWSO2APIDefinitions(apiDefinition)

      val expectedJson = """
                           |{
                           |  "paths" : {
                           |    "/application" : {
                           |      "get" : {
                           |        "responses" : {
                           |          "200" : {
                           |            "description" : "OK"
                           |          }
                           |        },
                           |        "x-auth-type" : "Application %26 Application User",
                           |        "x-throttling-tier" : "Unlimited"
                           |      }
                           |    },
                           |    "/friend" : {
                           |      "get" : {
                           |        "responses" : {
                           |          "200" : {
                           |            "description" : "OK"
                           |          }
                           |        },
                           |        "x-auth-type" : "None",
                           |        "x-throttling-tier" : "Unlimited"
                           |      }
                           |    },
                           |    "/user" : {
                           |      "get" : {
                           |        "parameters" : [ {
                           |          "name" : "forename",
                           |          "required" : true,
                           |          "in" : "query",
                           |          "type" : "string",
                           |          "description" : ""
                           |        }, {
                           |          "name" : "surname",
                           |          "required" : false,
                           |          "in" : "query",
                           |          "type" : "string",
                           |          "description" : ""
                           |        } ],
                           |        "responses" : {
                           |          "200" : {
                           |            "description" : "OK"
                           |          }
                           |        },
                           |        "x-auth-type" : "Application User",
                           |        "x-throttling-tier" : "Unlimited",
                           |        "x-scope" : "read:welcome"
                           |      }
                           |    },
                           |    "/user/employee" : {
                           |      "post" : {
                           |        "responses" : {
                           |          "200" : {
                           |            "description" : "OK"
                           |          }
                           |        },
                           |        "x-auth-type" : "Application User",
                           |        "x-throttling-tier" : "Unlimited",
                           |        "x-scope" : "read:welcome"
                           |      }
                           |    },
                           |    "/user/{friend}" : {
                           |      "get" : {
                           |        "parameters" : [ {
                           |          "name" : "friend",
                           |          "required" : true,
                           |          "in" : "path",
                           |          "type" : "string",
                           |          "description" : ""
                           |        }, {
                           |          "name" : "forename",
                           |          "required" : true,
                           |          "in" : "query",
                           |          "type" : "string",
                           |          "description" : ""
                           |        }, {
                           |          "name" : "surname",
                           |          "required" : false,
                           |          "in" : "query",
                           |          "type" : "string",
                           |          "description" : ""
                           |        } ],
                           |        "responses" : {
                           |          "200" : {
                           |            "description" : "OK"
                           |          }
                           |        },
                           |        "x-auth-type" : "Application User",
                           |        "x-throttling-tier" : "Unlimited",
                           |        "x-scope" : "read:welcome"
                           |      },
                           |      "delete" : {
                           |        "parameters" : [ {
                           |          "name" : "friend",
                           |          "required" : true,
                           |          "in" : "path",
                           |          "type" : "string",
                           |          "description" : ""
                           |        }, {
                           |          "name" : "forename",
                           |          "required" : true,
                           |          "in" : "query",
                           |          "type" : "string",
                           |          "description" : ""
                           |        }, {
                           |          "name" : "surname",
                           |          "required" : false,
                           |          "in" : "query",
                           |          "type" : "string",
                           |          "description" : ""
                           |        } ],
                           |        "responses" : {
                           |          "200" : {
                           |            "description" : "OK"
                           |          }
                           |        },
                           |        "x-auth-type" : "Application User",
                           |        "x-throttling-tier" : "Unlimited",
                           |        "x-scope" : "delete:welcome"
                           |      }
                           |    }
                           |  },
                           |  "info" : {
                           |    "title" : "Welcome API",
                           |    "version" : "1.0"
                           |  },
                           |  "swagger" : "2.0",
                           |  "x-wso2-security" : {
                           |    "apim" : {
                           |      "x-wso2-scopes" : [ {
                           |        "name" : "delete:welcome",
                           |        "key" : "delete:welcome",
                           |        "roles" : "admin",
                           |        "description" : ""
                           |      }, {
                           |        "name" : "read:welcome",
                           |        "key" : "read:welcome",
                           |        "roles" : "admin",
                           |        "description" : ""
                           |      } ]
                           |    }
                           |  }
                           |}
                         """.trim.stripMargin

      Json.prettyPrint(Json.toJson(wso2ApiDefinition.head.swagger)) shouldBe expectedJson
    }

  }

}
