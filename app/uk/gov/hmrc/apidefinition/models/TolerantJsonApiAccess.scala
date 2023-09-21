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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiAccess
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.play.json.Union

trait TolerantJsonApiAccess {

  private val readsPrivateApiAccess: Reads[ApiAccess.Private] = (
    (
      (
        (JsPath \ "allowlistedApplicationIds").read[List[ApplicationId]].orElse(
          (JsPath \ "whitelistedApplicationIds").readNullable[List[ApplicationId]].map(_.getOrElse(List.empty))
        )
      )
    ) and
      ((JsPath \ "isTrial").readNullable[Boolean].map(_.getOrElse(false)))
  )(ApiAccess.Private.apply _)

  private val writesPrivateApiAccess: OWrites[ApiAccess.Private] = (
    (JsPath \ "whitelistedApplicationIds").write[List[ApplicationId]] and // TODO - change to allowlisted once all readers are safe
      (JsPath \ "isTrial").write[Boolean]
  )(unlift(ApiAccess.Private.unapply))

  private implicit val formatPrivateApiAccess: OFormat[ApiAccess.Private]    = OFormat[ApiAccess.Private](readsPrivateApiAccess, writesPrivateApiAccess)
  private implicit val formatPublicApiAccess: OFormat[ApiAccess.PUBLIC.type] = Json.format[ApiAccess.PUBLIC.type]

  implicit val tolerantFormatApiAccess: Format[ApiAccess] = Union.from[ApiAccess]("type")
    .and[ApiAccess.PUBLIC.type]("PUBLIC")
    .and[ApiAccess.Private]("PRIVATE")
    .format
}

object TolerantJsonApiAccess extends TolerantJsonApiAccess
