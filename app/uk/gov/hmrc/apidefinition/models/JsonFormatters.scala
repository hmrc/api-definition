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
import uk.gov.hmrc.play.json.Union

object JsonFormatters {

  implicit val formatAWSQueryParameter = Json.format[AWSQueryParameter]
  implicit val formatAWSPathParameter  = Json.format[AWSPathParameter]

  implicit val formatAWSParameter = Union.from[AWSParameter]("in")
    .and[AWSQueryParameter]("query")
    .and[AWSPathParameter]("path")
    .format

  implicit val formatAWSResponse        = Json.format[AWSResponse]
  implicit val formatAWSHttpVerbDetails = Json.format[AWSHttpVerbDetails]
  implicit val formatAWSAPIInfo         = Json.format[AWSAPIInfo]
  implicit val formatAWSSwaggerDetails  = Json.format[AWSSwaggerDetails]

}
