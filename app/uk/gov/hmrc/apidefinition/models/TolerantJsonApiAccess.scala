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

import play.api.libs.json._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiAccess
import uk.gov.hmrc.play.json.Union

trait TolerantJsonApiAccess {

  private val readsPrivateApiAccess: Reads[ApiAccess.Private]    = ((JsPath \ "isTrial").readNullable[Boolean]).map(_.fold(ApiAccess.Private(false))(b => ApiAccess.Private(b)))
  private val writesPrivateApiAccess: OWrites[ApiAccess.Private] = Json.writes[ApiAccess.Private]

  private implicit val formatPrivateApiAccess: OFormat[ApiAccess.Private] = OFormat[ApiAccess.Private](readsPrivateApiAccess, writesPrivateApiAccess)

  implicit val tolerantFormatApiAccess: Format[ApiAccess] = Union.from[ApiAccess]("type")
    .andType[ApiAccess.PUBLIC.type]("PUBLIC", () => ApiAccess.PUBLIC)
    .and[ApiAccess.Private]("PRIVATE")
    .format
}

object TolerantJsonApiAccess extends TolerantJsonApiAccess
