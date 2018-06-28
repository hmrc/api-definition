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

import AuthType.AuthType
import ResourceThrottlingTier.ResourceThrottlingTier
import uk.gov.hmrc.apidefinition.models.WSO2ParameterType.WSO2ParameterType

case class WSO2APIDefinition(name: String,
                             context: String,
                             version: String,
                             subscribersCount: Int,
                             endpointConfig: WSO2EndpointConfig,
                             swagger: Option[WSO2SwaggerDetails])

case class WSO2EndpointConfig(production_endpoints: Option[WSO2Endpoint],
                              sandbox_endpoints: WSO2Endpoint,
                              endpoint_type: String = "http")

case class WSO2Response(description: String)

case class WSO2SwaggerScope(name: String, key: String, roles: String = "admin", description: String = "")

case class WSO2SwaggerDetails(paths: Map[String, Map[String, WSO2HttpVerbDetails]],
                              info: WSO2APIInfo,
                              swagger: String = "2.0",
                              `x-wso2-security`: Option[Map[String, Map[String, Seq[WSO2SwaggerScope]]]] = None,
                              basePath: Option[String] = None,
                              host: Option[String] = None)

case class WSO2APIInfo(title: String, version: String)

case class WSO2Endpoint(url: String)

case class WSO2Scope(name: String, key: String, description: String = "")

object WSO2ParameterType extends Enumeration {
  type WSO2ParameterType = Value

  val QUERY = Value("query")
  val PATH = Value("path")
  // val BODY = Value("body") - NOT IMPLEMENTED YET
}

abstract class WSO2Parameter(val name: String,
                             val required: Boolean,
                             val in: WSO2ParameterType,
                             val `type`: String = WSO2Parameter.defaultParameterType,
                             val description: String = WSO2Parameter.defaultParameterDescription) {
//  def paramType: WSO2ParameterType = in
}

object WSO2Parameter {
  val defaultParameterType = "string"
  val defaultParameterDescription = ""
}

case class WSO2QueryParameter(override val name: String,
                              override val required: Boolean,
                              override val in: WSO2ParameterType = WSO2ParameterType.QUERY,
                              override val `type`: String = WSO2Parameter.defaultParameterType,
                              override val description: String = WSO2Parameter.defaultParameterDescription)
  extends WSO2Parameter(name, required, in, `type`, description) {
}

case class WSO2PathParameter(override val name: String,
                             override val required: Boolean = true,
                             override val in: WSO2ParameterType = WSO2ParameterType.PATH,
                             override val `type`: String = WSO2Parameter.defaultParameterType,
                             override val description: String = WSO2Parameter.defaultParameterDescription)
  extends WSO2Parameter(name, required, in, `type`, description) {
}

// TODO: we might need `WSO2BodyParameter(in = WSO2ParameterType.BODY)`

case class WSO2HttpVerbDetails(parameters: Option[Seq[WSO2Parameter]],
                               responses: Map[String, WSO2Response],
                               `x-auth-type`: String,
                               `x-throttling-tier`: String,
                               `x-scope`: Option[String])

case class WSO2Resource(url_pattern: String, http_verbs: Map[String, WSO2HttpVerb])

case class WSO2HttpVerb(auth_type: String,
                        throttling_tier: String,
                        scope: Option[String] = None)

case class EndpointUrl(url: String) {

  private val parameterSegmentMatch = "^/\\{.*\\}$".r

  private val indexOfSlash = url.lastIndexOf('/')

  def isRoot = (url.indexOf('/') == url.lastIndexOf('/')) && url.length == 1

  def rootSegments = if (indexOfSlash >= 0) url.substring(0, indexOfSlash) else url

  def tailSegment = if (indexOfSlash >= 0) url.substring(indexOfSlash) else ""

  def tailSegmentIsParameter = parameterSegmentMatch.findFirstIn(tailSegment).isDefined
}

object WSO2APIDefinition {

  private val statusMap = Map(
    APIStatus.ALPHA -> "PUBLISHED",
    APIStatus.BETA -> "PUBLISHED",
    APIStatus.STABLE -> "PUBLISHED",
    APIStatus.PROTOTYPED -> "PUBLISHED",
    APIStatus.PUBLISHED -> "PUBLISHED",
    APIStatus.DEPRECATED -> "DEPRECATED",
    APIStatus.RETIRED -> "RETIRED")

  private val authTypeMap = Map(
    AuthType.NONE -> "None",
    AuthType.APPLICATION -> "Application %26 Application User",
    AuthType.USER -> "Application User")

  private val resourceThrottlingTierMap = Map(
    ResourceThrottlingTier.UNLIMITED -> "Unlimited")

  def wso2AuthType(authType: AuthType): String = {
    authTypeMap.getOrElse(authType, throw new IllegalArgumentException(s"Unknown Auth Type: $authType"))
  }

  def wso2ThrottlingTier(resourceThrottlingTier: ResourceThrottlingTier): String = {
    resourceThrottlingTierMap.getOrElse(resourceThrottlingTier, throw new IllegalArgumentException(s"Unknown Throttling Tier: $resourceThrottlingTier"))
  }

  def buildProductionUrl(apiVersion: APIVersion, apiDefinition: APIDefinition): Option[String] = {
    if (apiVersion.endpointsEnabled.getOrElse(false)) Some(apiDefinition.serviceBaseUrl)
    else None
  }

  def buildSandboxUrl(apiDefinition: APIDefinition): String = {
    s"${apiDefinition.serviceBaseUrl}/sandbox"
  }

  def wso2ApiName(version: String, apiDefinition: APIDefinition): String = {
    s"${apiDefinition.context.replaceAll("/", "--")}--$version"
  }

  def wso2ApiStatus(apiDefinition: APIDefinition, wso2APIDefinition: WSO2APIDefinition): String = {
    val status = apiDefinition.versions.filter(apiVersion => wso2APIDefinition.version.eq(apiVersion.version)).head.status
    statusMap.getOrElse(status, throw new IllegalArgumentException(s"Unknown Status: $status"))
  }

  def allAvailableWSO2SubscriptionTiers: Seq[String] = {
    SubscriptionThrottlingTier.values.toSeq map (_.toString)
  }

}
