/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.utils

import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.models.WSO2APIDefinition._

import scala.collection.immutable.TreeMap

object WSO2PayloadHelper {

  def buildWSO2Security(endpoints: Seq[Endpoint]): Option[Map[String, Map[String, Seq[WSO2SwaggerScope]]]] = {

    def buildAllScopes(): Seq[WSO2SwaggerScope] = {
      endpoints.flatMap(_.scope).distinct.map {
          scope: String => WSO2SwaggerScope(name = scope, key = scope)
        }
    }.sortBy(_.key)

    Option(buildAllScopes()).filter(_.nonEmpty).map {
      scopes: Seq[WSO2SwaggerScope] => Map("apim" -> Map("x-wso2-scopes" -> scopes))
    }
  }


  def buildWSO2PathParameters(endpoint: Endpoint): Seq[WSO2PathParameter] = {
    RegexHelper.extractPathParameters(endpoint.uriPattern).map {
      param: String => WSO2PathParameter(name = param)
    }
  }

  def buildWSO2QueryParameters(endpoint: Endpoint): Seq[WSO2QueryParameter] = {
    endpoint.queryParameters.getOrElse(Seq()).map {
      p: Parameter => WSO2QueryParameter(name = p.name, required = p.required)
    }.sortBy(_.name)
  }

  def buildWSO2Parameters(endpoint: Endpoint): Option[Seq[WSO2Parameter]] = {
    Option(buildWSO2PathParameters(endpoint) ++ buildWSO2QueryParameters(endpoint)).filter(_.nonEmpty)
  }


  private def buildWSO2Paths(apiVersion: APIVersion): Map[String, Map[String, WSO2HttpVerbDetails]] = {

    def buildWSO2HttpVerbDetails(e: Endpoint): WSO2HttpVerbDetails = {
      WSO2HttpVerbDetails(
        parameters = buildWSO2Parameters(e),
        responses = Map("200" -> WSO2Response(description = "OK")),
        `x-auth-type` = wso2AuthType(e.authType),
        `x-throttling-tier` = wso2ThrottlingTier(e.throttlingTier),
        `x-scope` = e.scope)
    }

    def groupEndpointsByResource(endpoints: Seq[Endpoint]): Map[String, Seq[Endpoint]] = {
      endpoints.groupBy(_.uriPattern)
    }

    def buildHttpVerbsDetails(resourceToEndpoints: Map[String, Seq[Endpoint]]): Map[String, Map[String, WSO2HttpVerbDetails]] = {
      resourceToEndpoints.mapValues { endpoints: Seq[Endpoint] =>
        endpoints.map { e: Endpoint =>
          (e.method.toString.toLowerCase, buildWSO2HttpVerbDetails(e))
        }.groupBy(_._1).mapValues(_.head._2)
      }
    }

    // sorting alphabetically by resource
    import scala.math.Ordering.String
    TreeMap() ++ buildHttpVerbsDetails(groupEndpointsByResource(apiVersion.endpoints))
  }

  def buildWSO2SwaggerDetails(apiName: String, apiVersion: APIVersion): WSO2SwaggerDetails = {
    WSO2SwaggerDetails(
      paths = buildWSO2Paths(apiVersion),
      info = WSO2APIInfo(apiName, apiVersion.version),
      `x-wso2-security` = buildWSO2Security(apiVersion.endpoints))
  }

  def buildAWSSwaggerDetails(apiName: String, apiVersion: APIVersion, basePath: String, host: String): WSO2SwaggerDetails = {
    WSO2SwaggerDetails(
      paths = buildWSO2Paths(apiVersion),
      info = WSO2APIInfo(apiName, apiVersion.version),
      basePath = Some(s"/$basePath"),
      host = Some(host))
  }

  private def buildWSO2EndpointConfig(apiDefinition: APIDefinition, apiVersion: APIVersion): WSO2EndpointConfig = {
    WSO2EndpointConfig(
      production_endpoints = buildProductionUrl(apiVersion, apiDefinition).map(WSO2Endpoint),
      sandbox_endpoints = WSO2Endpoint(buildSandboxUrl(apiDefinition))
    )
  }

  def buildWSO2APIDefinitions(apiDefinition: APIDefinition): Seq[WSO2APIDefinition] = {

    def buildWSO2APIDefinition(apiVersion: APIVersion) = {
      WSO2APIDefinition(
        name = wso2ApiName(apiVersion.version, apiDefinition),
        context = s"{version}/${apiDefinition.context}",
        version = apiVersion.version,
        subscribersCount = 0,
        endpointConfig = buildWSO2EndpointConfig(apiDefinition, apiVersion),
        swagger = Some(buildWSO2SwaggerDetails(apiDefinition.name, apiVersion))
      )
    }

    apiDefinition.versions.map(buildWSO2APIDefinition)
  }

}
