/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json, OFormat}

object ErrorCode extends Enumeration {

  type ErrorCode = Value

  val INVALID_REQUEST_PAYLOAD = Value("INVALID_REQUEST_PAYLOAD")
  val INTERNAL_SERVER_ERROR = Value("INTERNAL_SERVER_ERROR")
  val API_DEFINITION_NOT_FOUND = Value("API_DEFINITION_NOT_FOUND")
  val API_INVALID_JSON = Value("API_INVALID_JSON")
  val CONTEXT_ALREADY_DEFINED = Value("CONTEXT_ALREADY_DEFINED")
  val UNSUPPORTED_ACCESS_TYPE = Value("UNSUPPORTED_ACCESS_TYPE")
}



case class ValidationErrors(code: ErrorCode.Value, messages: Seq[String])

object ValidationErrors {
  implicit val format1: Format[ErrorCode.Value] = EnumJson.enumFormat(ErrorCode)
  implicit val format2: OFormat[ValidationErrors] = Json.format[ValidationErrors]
}

case class ErrorResponse(code: ErrorCode.Value, message: String, details: Option[Seq[FieldErrorDescription]] = None)

object ErrorResponse {
  implicit val format1 = Json.format[FieldErrorDescription]
  implicit val format2 = EnumJson.enumFormat(ErrorCode)
  implicit val format3 = Json.format[ErrorResponse]
}

case class FieldErrorDescription(field: String, message: String)

object FieldErrorDescription {
  implicit val format = Json.format[FieldErrorDescription]
}
