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
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

trait TolerantJsonEndpoint {

  private val endpointReads: Reads[Endpoint] = (
    (JsPath \ "uriPattern").read[String] and
      (JsPath \ "endpointName").read[String] and
      (JsPath \ "method").read[HttpMethod] and
      (JsPath \ "authType").read[AuthType] and
      (JsPath \ "throttlingTier").read[ResourceThrottlingTier] and
      (JsPath \ "scope").readNullable[String] and
      ((JsPath \ "queryParameters").readNullable[List[QueryParameter]].map(_.getOrElse(List.empty)))
  )(Endpoint.apply _)

  private val endpointWrites: OWrites[Endpoint] = Json.writes[Endpoint]

  implicit val tolerantFormatEndpoint: Format[Endpoint] = OFormat[Endpoint](endpointReads, endpointWrites)
}

object TolerantJsonEndpoint extends TolerantJsonEndpoint
