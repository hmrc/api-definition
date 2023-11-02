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
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus

trait TolerantJsonApiStatus {

  private val readsApiStatus: Reads[ApiStatus] = Reads.JsStringReads.preprocess {
    case JsString("PUBLISHED")  => JsString("STABLE")
    case JsString("PROTOTYPED") => JsString("BETA")
  }.andThen(ApiStatus.format)

  private val writesApiStatus: Writes[ApiStatus] = ApiStatus.format

  implicit val tolerantFormatApiStatus: Format[ApiStatus] = Format[ApiStatus](readsApiStatus, writesApiStatus)

}

object TolerantJsonApiStatus extends TolerantJsonApiStatus
