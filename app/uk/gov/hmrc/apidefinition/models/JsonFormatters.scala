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

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import play.api.libs.json._
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apidefinition.models.AWSParameterType._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.APIVersion

object JsonFormatters {

  implicit val formatApiCategoryDetails     = Json.format[ApiCategoryDetails]
  
  private val dateTimeFormatter = ISODateTimeFormat.dateTime().withZoneUTC()

  implicit val dateTimeReads: Reads[DateTime] = new Reads[DateTime] {

    override def reads(json: JsValue): JsResult[DateTime] = {
      json match {
        case JsString(s) => JsSuccess(dateTimeFormatter.parseDateTime(s))
        case _           => JsError(s"Unexpected format for DateTime: $json")
      }
    }
  }

  implicit val dateTimeWrites: Writes[DateTime] = new Writes[DateTime] {

    override def writes(dateTime: DateTime): JsValue = {
      JsString(dateTimeFormatter.print(dateTime))
    }
  }

  implicit val dateTimeFormats = Format(fjs = dateTimeReads, tjs = dateTimeWrites)

 
  implicit val formatAWSParameterType  = EnumJson.enumFormat(AWSParameterType)
  implicit val formatAWSQueryParameter = Json.format[AWSQueryParameter]
  implicit val formatAWSPathParameter  = Json.format[AWSPathParameter]

  implicit val formatAWSParameter = Union.from[AWSParameter]("in")
    .and[AWSQueryParameter](QUERY.toString)
    .and[AWSPathParameter](PATH.toString)
    .format

  implicit val formatAWSResponse        = Json.format[AWSResponse]
  implicit val formatAWSHttpVerbDetails = Json.format[AWSHttpVerbDetails]
  implicit val formatAWSAPIInfo         = Json.format[AWSAPIInfo]
  implicit val formatAWSSwaggerDetails  = Json.format[AWSSwaggerDetails]

  import play.api.libs.functional.syntax._ // Combinator syntax

  val apiVersionReads: Reads[APIVersion] = (
    (JsPath \ "version").read[ApiVersionNbr] and
      (JsPath \ "status").read[ApiStatus] and
      (JsPath \ "access").readNullable[ApiAccess] and
      (JsPath \ "endpoints").read[List[Endpoint]] and
      (JsPath \ "endpointsEnabled").readNullable[Boolean] and
      (JsPath \ "awsRequestId").readNullable[String] and
      ((JsPath \ "versionSource").read[ApiVersionSource] or Reads.pure[ApiVersionSource](ApiVersionSource.UNKNOWN))
  )(uk.gov.hmrc.apiplatform.modules.apis.domain.models.APIVersion.apply _)

  val apiVersionWrites: OWrites[APIVersion] = Json.writes[APIVersion]
  implicit val formatApiVersion             = OFormat[APIVersion](apiVersionReads, apiVersionWrites)

  implicit val formatAPIDefinition: OFormat[APIDefinition] = Json.format[APIDefinition]


  implicit val formatExtendedAPIVersion                                    = Json.format[ExtendedAPIVersion]
  implicit val formatExtendedAPIDefinition: OFormat[ExtendedAPIDefinition] = Json.format[ExtendedAPIDefinition]


}

object EnumJson {

  def enumReads[E <: Enumeration](enumValue: E): Reads[E#Value] = new Reads[E#Value] {

    override def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) =>
        try {
          JsSuccess(enumValue.withName(s))
        } catch {
          case _: NoSuchElementException =>
            JsError(s"Enumeration expected of type: '${enumValue.getClass}', but it does not contain '$s'")
        }

      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    override def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enumValue: E): Format[E#Value] = {
    Format(enumReads(enumValue), enumWrites)
  }

}
