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

import org.joda.time.DateTime

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersion

case class APIDefinition(
    serviceName: String,
    serviceBaseUrl: String,
    name: String,
    description: String,
    context: ApiContext,
    versions: List[ApiVersion],
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
    productionAvailability: Option[ApiAvailability],
    sandboxAvailability: Option[ApiAvailability]
  )




