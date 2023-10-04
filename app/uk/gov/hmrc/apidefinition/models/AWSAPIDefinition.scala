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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import play.api.libs.json.Format
import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting

case class AWSAPIDefinition(name: String, context: ApiContext, version: ApiVersionNbr, subscribersCount: Int, endpointConfig: AWSEndpointConfig, swagger: Option[AWSSwaggerDetails])

case class AWSEndpointConfig(production_endpoints: Option[AWSEndpoint], sandbox_endpoints: AWSEndpoint, endpoint_type: String = "http")

case class AWSEndpoint(url: String)

case class AWSSwaggerDetails(
    paths: Map[String, Map[String, AWSHttpVerbDetails]],
    info: AWSAPIInfo,
    swagger: String = "2.0",
    basePath: Option[String] = None,
    host: Option[String] = None
  )

case class AWSAPIInfo(title: String, version: ApiVersionNbr)

case class AWSHttpVerbDetails(
    parameters: Option[Seq[AWSParameter]],
    responses: Map[String, AWSResponse],
    `x-auth-type`: String,
    `x-throttling-tier`: String,
    `x-scope`: Option[String]
  )
  
sealed trait AWSParameterType

object AWSParameterType {
  case object QUERY extends AWSParameterType
  case object PATH extends AWSParameterType

  val values = Set[AWSParameterType](QUERY, PATH)

  def apply(text: String): Option[AWSParameterType] = AWSParameterType.values.find(_.toString() == text.toUpperCase)

  def unsafeApply(text: String): AWSParameterType = apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid AWS Parameter Type"))

  implicit val format: Format[AWSParameterType] = SealedTraitJsonFormatting.createFormatFor[AWSParameterType]("AWS Parameter Type", apply, (t) => t.toString().toLowerCase())
}

case class AWSResponse(description: String)

abstract class AWSParameter(
    val name: String,
    val required: Boolean,
    val in: AWSParameterType,
    val `type`: String = AWSParameter.defaultParameterType,
    val description: String = AWSParameter.defaultParameterDescription
  ) {}

object AWSParameter {
  val defaultParameterType        = "string"
  val defaultParameterDescription = ""
}

case class AWSQueryParameter(
    override val name: String,
    override val required: Boolean,
    override val in: AWSParameterType = AWSParameterType.QUERY,
    override val `type`: String = AWSParameter.defaultParameterType,
    override val description: String = AWSParameter.defaultParameterDescription
  ) extends AWSParameter(name, required, in, `type`, description) {}

case class AWSPathParameter(
    override val name: String,
    override val required: Boolean = true,
    override val in: AWSParameterType = AWSParameterType.PATH,
    override val `type`: String = AWSParameter.defaultParameterType,
    override val description: String = AWSParameter.defaultParameterDescription
  ) extends AWSParameter(name, required, in, `type`, description) {}

object AWSAPIDefinition {

  private val statusMap = Map[ApiStatus, String](
    ApiStatus.ALPHA      -> "PUBLISHED",
    ApiStatus.BETA       -> "PUBLISHED",
    ApiStatus.STABLE     -> "PUBLISHED",
    ApiStatus.DEPRECATED -> "DEPRECATED",
    ApiStatus.RETIRED    -> "RETIRED"
  )

  private val authTypeMap = Map[AuthType, String](
    AuthType.NONE        -> "None",
    AuthType.APPLICATION -> "Application %26 Application User",
    AuthType.USER        -> "Application User"
  )

  private val resourceThrottlingTierMap = Map[ResourceThrottlingTier, String](ResourceThrottlingTier.UNLIMITED -> "Unlimited")

  def awsAuthType(authType: AuthType): String = {
    authTypeMap.getOrElse(authType, throw new IllegalArgumentException(s"Unknown Auth Type: $authType"))
  }

  def awsThrottlingTier(resourceThrottlingTier: ResourceThrottlingTier): String = {
    resourceThrottlingTierMap.getOrElse(resourceThrottlingTier, throw new IllegalArgumentException(s"Unknown Throttling Tier: $resourceThrottlingTier"))
  }

  def awsApiGatewayName(version: ApiVersionNbr, apiDefinition: ApiDefinition): String = {
    def asAwsSafeString(context: ApiContext): String = context.value.replaceAll("/", "--")

    s"${asAwsSafeString(apiDefinition.context)}--$version"
  }

  def awsApiStatus(apiDefinition: ApiDefinition, awsAPIDefinition: AWSAPIDefinition): String = {
    val status: ApiStatus = apiDefinition.versions.filter(apiVersion => awsAPIDefinition.version == apiVersion.versionNbr).head.status
    statusMap.getOrElse(status, throw new IllegalArgumentException(s"Unknown Status: $status"))
  }
}
