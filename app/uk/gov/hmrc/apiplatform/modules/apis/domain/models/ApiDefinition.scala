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

package uk.gov.hmrc.apiplatform.modules.apis.domain.models

import org.joda.time.DateTime

case class ApiDefinition(
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

object ApiDefinition {
  import play.api.libs.json._
  import DateTimeJsonFormatters._
  implicit val formatAPIDefinition: OFormat[ApiDefinition] = Json.format[ApiDefinition]
}