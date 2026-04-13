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
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccess, ApiAccessType}

trait TolerantJsonApiAccess {

  private val readsApiAccess: Reads[ApiAccess] = (
    (JsPath \ "type").read[ApiAccessType] and
      (JsPath \ "isTrial").readNullable[Boolean].map(_.getOrElse(false))
  )((accessType, isTrial) =>
    (accessType, isTrial) match {
      case (ApiAccessType.PRIVATE, false) => ApiAccess.INTERNAL
      case (ApiAccessType.PRIVATE, true)  => ApiAccess.CONTROLLED
      case (ApiAccessType.PUBLIC, _)      => ApiAccess.PUBLIC
      case (ApiAccessType.CONTROLLED, _)  => ApiAccess.CONTROLLED
      case (ApiAccessType.INTERNAL, _)    => ApiAccess.INTERNAL
    }
  )

  private val writesApiAccess: OWrites[ApiAccess] =
    (JsPath \ "type").write[ApiAccessType]
      .contramap {
        case ApiAccess.Private(false) => ApiAccessType.INTERNAL
        case ApiAccess.Private(true)  => ApiAccessType.CONTROLLED
        case ApiAccess.PUBLIC         => ApiAccessType.PUBLIC
        case ApiAccess.CONTROLLED     => ApiAccessType.CONTROLLED
        case ApiAccess.INTERNAL       => ApiAccessType.INTERNAL
      }

  implicit val tolerantFormatApiAccess: OFormat[ApiAccess] = OFormat[ApiAccess](readsApiAccess, writesApiAccess)
}

object TolerantJsonApiAccess extends TolerantJsonApiAccess
