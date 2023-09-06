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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

case class ApiVersion(
    version: ApiVersionNbr,
    status: ApiStatus,
    access: Option[ApiAccess] = Some(ApiAccess.PUBLIC),
    endpoints: List[Endpoint],
    endpointsEnabled: Option[Boolean] = None,
    awsRequestId: Option[String] = None,
    versionSource: ApiVersionSource = ApiVersionSource.UNKNOWN
  )

object ApiVersion {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._ // Combinator syntax

  val apiVersionReads: Reads[ApiVersion] = (
    (JsPath \ "version").read[ApiVersionNbr] and
      (JsPath \ "status").read[ApiStatus] and
      (JsPath \ "access").readNullable[ApiAccess] and
      (JsPath \ "endpoints").read[List[Endpoint]] and
      (JsPath \ "endpointsEnabled").readNullable[Boolean] and
      (JsPath \ "awsRequestId").readNullable[String] and
      ((JsPath \ "versionSource").read[ApiVersionSource] or Reads.pure[ApiVersionSource](ApiVersionSource.UNKNOWN))
  )(uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersion.apply _)

  val apiVersionWrites: OWrites[ApiVersion] = Json.writes[ApiVersion]
  implicit val formatApiVersion             = OFormat[ApiVersion](apiVersionReads, apiVersionWrites)
}