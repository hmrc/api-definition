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

package uk.gov.hmrc.apidefinition.models

import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json, Reads, Writes}
import uk.gov.hmrc.apidefinition.models.APICategory.APICategory
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models.AuthType.AuthType
import uk.gov.hmrc.apidefinition.models.HttpMethod.HttpMethod
import uk.gov.hmrc.apidefinition.models.ResourceThrottlingTier.ResourceThrottlingTier

case class APIDefinition(serviceName: String,
                         serviceBaseUrl: String,
                         name: String,
                         description: String,
                         context: String,
                         versions: Seq[APIVersion],
                         requiresTrust: Option[Boolean],
                         isTestSupport: Option[Boolean] = None,
                         lastPublishedAt: Option[DateTime] = None,
                         categories: Option[Seq[APICategory]] = None)

case class ExtendedAPIDefinition(serviceName: String,
                                 serviceBaseUrl: String,
                                 name: String,
                                 description: String,
                                 context: String,
                                 requiresTrust: Boolean,
                                 isTestSupport: Boolean,
                                 versions: Seq[ExtendedAPIVersion],
                                 lastPublishedAt: Option[DateTime])

case class ExtendedAPIVersion(version: String,
                              status: APIStatus,
                              endpoints: Seq[Endpoint],
                              productionAvailability: Option[APIAvailability],
                              sandboxAvailability: Option[APIAvailability])

case class APIAvailability(endpointsEnabled: Boolean, access: APIAccess, loggedIn: Boolean, authorised: Boolean)

case class APIVersion(version: String,
                      status: APIStatus,
                      access: Option[APIAccess] = Some(PublicAPIAccess()),
                      endpoints: Seq[Endpoint],
                      endpointsEnabled: Option[Boolean] = None,
                      awsApiId: Option[String] = None)

// API resource (also called API endpoint)
case class Endpoint(uriPattern: String,
                    endpointName: String,
                    method: HttpMethod,
                    authType: AuthType,
                    throttlingTier: ResourceThrottlingTier,
                    scope: Option[String] = None,
                    queryParameters: Option[Seq[Parameter]] = None)

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

case class PrivateAPIAccess(whitelistedApplicationIds: Seq[String], isTrial: Option[Boolean] = None) extends APIAccess
object PrivateAPIAccess {
  implicit val format2 = Json.format[PrivateAPIAccess]
}

object APICategory extends Enumeration {
  type APICategory = Value

  val EXAMPLE, AGENTS, BUSINESS_RATES, CHARITIES, CONSTRUCTION_INDUSTRY_SCHEME, CORPORATION_TAX, CUSTOMS, ESTATES, HELP_TO_SAVE, INCOME_TAX_MTD,
    LIFETIME_ISA, MARRIAGE_ALLOWANCE, NATIONAL_INSURANCE, PAYE, PENSIONS, PRIVATE_GOVERNMENT,
    RELIEF_AT_SOURCE, SELF_ASSESSMENT, STAMP_DUTY, TRUSTS, VAT, VAT_MTD, OTHER = Value
}

object APIStatus extends Enumeration {
  type APIStatus = Value
  val PROTOTYPED, PUBLISHED, ALPHA, BETA, STABLE, DEPRECATED, RETIRED = Value
}

object AuthType extends Enumeration {
  type AuthType = Value
  val NONE, APPLICATION, USER = Value
}

object HttpMethod extends Enumeration {
  type HttpMethod = Value
  val GET, POST, PUT, DELETE, OPTIONS = Value
}

object ResourceThrottlingTier extends Enumeration {
  type ResourceThrottlingTier = Value
  val UNLIMITED = Value
}

object SubscriptionThrottlingTier extends Enumeration {
  type ThrottlingTier = Value
  val BRONZE_SUBSCRIPTION, SILVER_SUBSCRIPTION, GOLD_SUBSCRIPTION, PLATINUM_SUBSCRIPTION = Value
}
