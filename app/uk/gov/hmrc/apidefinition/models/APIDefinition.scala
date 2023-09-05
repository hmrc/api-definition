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

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import org.joda.time.DateTime

import play.api.libs.json.{JsObject, Json, Reads}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

// scalastyle:off number.of.types

case class APIDefinition(
    serviceName: String,
    serviceBaseUrl: String,
    name: String,
    description: String,
    context: ApiContext,
    versions: List[APIVersion],
    requiresTrust: Option[Boolean],
    isTestSupport: Option[Boolean] = None,
    lastPublishedAt: Option[DateTime] = None,
    categories: Option[List[ApiCategory]] = None
  )

case class ExtendedAPIDefinition(
    serviceName: String,
    serviceBaseUrl: String,
    name: String,
    description: String,
    context: ApiContext,
    requiresTrust: Boolean,
    isTestSupport: Boolean,
    versions: List[ExtendedAPIVersion],
    lastPublishedAt: Option[DateTime]
  )

case class ExtendedAPIVersion(
    version: ApiVersionNbr,
    status: ApiStatus,
    endpoints: List[Endpoint],
    productionAvailability: Option[APIAvailability],
    sandboxAvailability: Option[APIAvailability]
  )

case class APIAvailability(endpointsEnabled: Boolean, access: APIAccess, loggedIn: Boolean, authorised: Boolean)

case class APIVersion(
    version: ApiVersionNbr,
    status: ApiStatus,
    access: Option[APIAccess] = Some(PublicAPIAccess()),
    endpoints: List[Endpoint],
    endpointsEnabled: Option[Boolean] = None,
    awsRequestId: Option[String] = None,
    versionSource: ApiVersionSource = ApiVersionSource.UNKNOWN
  )

// API resource (also called API endpoint)
case class Endpoint(
    uriPattern: String,
    endpointName: String,
    method: HttpMethod,
    authType: AuthType,
    throttlingTier: ResourceThrottlingTier,
    scope: Option[String] = None,
    queryParameters: Option[List[Parameter]] = None
  )

// Query Parameter
case class Parameter(name: String, required: Boolean = false)

case class PublishingException(message: String) extends Exception(message)

object APIAccessType extends Enumeration {
  type APIAccessType = Value
  val PRIVATE, PUBLIC = Value
}

trait APIAccess

case class PublicAPIAccess() extends APIAccess

object PublicAPIAccess {
  implicit val strictReads = Reads[PublicAPIAccess](json => json.validate[JsObject].filter(_.values.isEmpty).map(_ => PublicAPIAccess()))
}

case class PrivateAPIAccess(whitelistedApplicationIds: List[String], isTrial: Option[Boolean] = None) extends APIAccess

object PrivateAPIAccess {
  implicit val format2 = Json.format[PrivateAPIAccess]
}


// scalastyle:on number.of.types
