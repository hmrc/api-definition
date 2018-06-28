/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.apidefinition.models.APIAccessType._
import uk.gov.hmrc.apidefinition.models.WSO2ParameterType._
import uk.gov.hmrc.play.json.Union

object JsonFormatters {

  implicit val formatAPIStatus = EnumJson.enumFormat(APIStatus)
  implicit val formatAPIAccessType = EnumJson.enumFormat(APIAccessType)
  implicit val formatAuthType = EnumJson.enumFormat(AuthType)
  implicit val formatHttpMethod = EnumJson.enumFormat(HttpMethod)
  implicit val formatResourceThrottlingTier = EnumJson.enumFormat(ResourceThrottlingTier)

  implicit object apiAccessWrites extends Writes[APIAccess] {
    override def writes(access: APIAccess) = access match {
      case _: PublicAPIAccess => Json.obj("type" -> PUBLIC)
      case privApi: PrivateAPIAccess => Json.obj("type" -> PRIVATE, "whitelistedApplicationIds" -> privApi.whitelistedApplicationIds)
      case acc => throw new RuntimeException(s"Unknown API Access $acc")
    }
  }

  implicit val apiAccessReads: Reads[APIAccess] = (
    (JsPath \ "type").read[APIAccessType.APIAccessType] and
      (JsPath \ "whitelistedApplicationIds").readNullable[Seq[String]] tupled) map {
    case (PUBLIC, None | Some(Seq())) => PublicAPIAccess()
    case (PRIVATE, Some(whitelistedApplicationIds: Seq[String])) => PrivateAPIAccess(whitelistedApplicationIds)
    case (PRIVATE, None) => throw new RuntimeException("API Access 'PRIVATE' should define 'whitelistedApplicationIds'")
    case unknownApiAccess => throw new RuntimeException(s"Unknown API Access $unknownApiAccess")
  }

  implicit val formatParameter = Json.format[Parameter]
  implicit val formatEndpoint = Json.format[Endpoint]
  implicit val formatAPIVersion = Json.format[APIVersion]

  private val dateTimeFormatter = ISODateTimeFormat.dateTime().withZoneUTC()

  implicit val dateTimeReads: Reads[DateTime] = new Reads[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] = {
      json match {
        case JsString(s) => JsSuccess(dateTimeFormatter.parseDateTime(s))
        case _ => JsError(s"Unexpected format for DateTime: $json")
      }
    }
  }

  implicit val dateTimeWrites: Writes[DateTime] = new Writes[DateTime] {
    override def writes(dateTime: DateTime): JsValue = {
      JsString(dateTimeFormatter.print(dateTime))
    }
  }

  implicit val dateTimeFormats = Format(fjs = dateTimeReads, tjs = dateTimeWrites)

  implicit val formatAPIDefinition = Json.format[APIDefinition]
  implicit val formatAPIAvailability = Json.format[APIAvailability]
  implicit val formatExtendedAPIVersion = Json.format[ExtendedAPIVersion]
  implicit val formatExtendedAPIDefinition = Json.format[ExtendedAPIDefinition]

  implicit val formatWSO2HttpVerb = Json.format[WSO2HttpVerb]
  implicit val formatWSO2Resource = Json.format[WSO2Resource]
  implicit val formatWSO2Scope = Json.format[WSO2Scope]
  implicit val formatWSO2Endpoint = Json.format[WSO2Endpoint]
  implicit val formatWSO2EndpointConfig = Json.format[WSO2EndpointConfig]

  implicit val formatWSO2ParameterType = EnumJson.enumFormat(WSO2ParameterType)
  implicit val formatWSO2QueryParameter = Json.format[WSO2QueryParameter]
  implicit val formatWSO2PathParameter = Json.format[WSO2PathParameter]
  implicit val formatWSO2Parameter = Union.from[WSO2Parameter]("in")
    .and[WSO2QueryParameter](QUERY.toString)
    .and[WSO2PathParameter](PATH.toString)
    .format

  implicit val formatWSO2Response = Json.format[WSO2Response]
  implicit val formatWSO2HttpVerbDetails = Json.format[WSO2HttpVerbDetails]
  implicit val formatWSO2APIInfo = Json.format[WSO2APIInfo]
  implicit val formatWSO2SwaggerScope = Json.format[WSO2SwaggerScope]
  implicit val formatWSO2SwaggerDetails = Json.format[WSO2SwaggerDetails]
}

object EnumJson {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    override def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) =>
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException =>
            JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not contain '$s'")
        }

      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    override def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }

}
