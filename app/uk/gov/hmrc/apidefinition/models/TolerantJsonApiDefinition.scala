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

import java.time.Instant

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.*
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition.ServiceBaseUrl
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

trait TolerantJsonApiDefinition extends TolerantJsonApiVersion {

  private val apiDefinitionReads: Reads[StoredApiDefinition] = (
    (JsPath \ "serviceName").read[ServiceName] and
      (JsPath \ "serviceBaseUrl").read[ServiceBaseUrl] and
      (JsPath \ "name").read[ApiDefinition.Name] and
      (JsPath \ "description").read[ApiDefinition.Description] and
      (JsPath \ "context").read[ApiContext] and
      (JsPath \ "versions").read[List[ApiVersion]] and
      ((JsPath \ "isTestSupport").readNullable[Boolean].map(_.getOrElse(false))) and
      (JsPath \ "lastPublishedAt").readNullable[Instant] and
      ((JsPath \ "categories").readNullable[List[ApiCategory]].map(_.getOrElse(List.empty)))
  )((a, b, c, d, e, f, g, h, i) => StoredApiDefinition(a, b, c, d, e, f, g, h, i))

  private val apiDefinitionWrites: OWrites[StoredApiDefinition] = Json.writes[StoredApiDefinition]

  implicit val tolerantFormatApiDefinition: OFormat[StoredApiDefinition] = OFormat[StoredApiDefinition](apiDefinitionReads, apiDefinitionWrites)
}

object TolerantJsonApiDefinition extends TolerantJsonApiDefinition
