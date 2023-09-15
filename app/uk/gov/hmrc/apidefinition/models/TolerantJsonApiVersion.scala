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
import play.api.libs.functional.syntax._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

trait TolerantJsonApiVersion extends TolerantJsonApiAccess with TolerantJsonEndpoint {
  private val apiVersionReads: Reads[ApiVersion] = (
    (JsPath \ "version").read[ApiVersionNbr] and
      (JsPath \ "status").read[ApiStatus] and
      ((JsPath \ "access").readNullable[ApiAccess].map(_.getOrElse(ApiAccess.PUBLIC))) and
      (JsPath \ "endpoints").read[List[Endpoint]] and
      ((JsPath \ "endpointsEnabled").readNullable[Boolean].map(_.getOrElse(true))) and
      (JsPath \ "awsRequestId").readNullable[String] and
      ((JsPath \ "versionSource").readNullable[ApiVersionSource].map(_.getOrElse(ApiVersionSource.UNKNOWN)))
  )(ApiVersion.apply _)

  private val apiVersionWrites: OWrites[ApiVersion] = Json.writes[ApiVersion]
  
  implicit val tolerantFormatApiVersion = OFormat[ApiVersion](apiVersionReads, apiVersionWrites)
}

object TolerantJsonApiVersion extends TolerantJsonApiVersion