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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.apidefinition.models.APIAccessType._
import uk.gov.hmrc.apidefinition.models.AWSParameterType._
import uk.gov.hmrc.play.json.Union

object JsonFormatters {

  implicit val formatAPICategoryDetails     = Json.format[APICategoryDetails]
  implicit val formatAPIStatus              = EnumJson.enumFormat(APIStatus)
  implicit val formatAPIAccessType          = EnumJson.enumFormat(APIAccessType)
  implicit val formatAuthType               = EnumJson.enumFormat(AuthType)
  implicit val formatHttpMethod             = EnumJson.enumFormat(HttpMethod)
  implicit val formatResourceThrottlingTier = EnumJson.enumFormat(ResourceThrottlingTier)

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

  implicit object apiAccessWrites extends Writes[APIAccess] {

    private val privApiWrites: OWrites[(APIAccessType, List[String], Option[Boolean])] = (
      (JsPath \ "type").write[APIAccessType] and
        (JsPath \ "whitelistedApplicationIds").write[List[String]] and
        (JsPath \ "isTrial").writeNullable[Boolean]
    ).tupled

    override def writes(access: APIAccess) = access match {
      case _: PublicAPIAccess        => Json.obj("type" -> PUBLIC)
      case privApi: PrivateAPIAccess => privApiWrites.writes((PRIVATE, privApi.whitelistedApplicationIds, privApi.isTrial))
      case acc                       => throw new RuntimeException(s"Unknown API Access $acc")
    }
  }

  implicit val apiAccessReads: Reads[APIAccess] =
    (
      (JsPath \ "type").read[APIAccessType.APIAccessType] and
        (JsPath \ "whitelistedApplicationIds").readNullable[List[String]] and
        (JsPath \ "isTrial").readNullable[Boolean] tupled
    ) map {
      case (PUBLIC, None | Some(Nil), _)                                     => PublicAPIAccess()
      case (PRIVATE, Some(whitelistedApplicationIds: List[String]), isTrial) => PrivateAPIAccess(whitelistedApplicationIds, isTrial)
      case (PRIVATE, None, isTrial)                                          => PrivateAPIAccess(Nil, isTrial)
      case unknownApiAccess                                                  => throw new RuntimeException(s"Unknown API Access $unknownApiAccess")
    }

  implicit val apiVersionSourceJF: Format[ApiVersionSource] = new Format[ApiVersionSource] {

    def reads(json: JsValue): JsResult[ApiVersionSource] = json match {
      case JsString(RAML.asText)    => JsSuccess(RAML)
      case JsString(OAS.asText)     => JsSuccess(OAS)
      case JsString(UNKNOWN.asText) => JsSuccess(UNKNOWN)
      case e                        => JsError(s"Cannot parse source value from '$e'")
    }

    def writes(foo: ApiVersionSource): JsValue = {
      JsString(foo.asText)
    }
  }

  import play.api.libs.functional.syntax._ // Combinator syntax

  implicit val formatParameter = Json.format[Parameter]
  implicit val formatEndpoint  = Json.format[Endpoint]

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

  implicit val formatAPIAvailability                                       = Json.format[APIAvailability]
  implicit val formatExtendedAPIVersion                                    = Json.format[ExtendedAPIVersion]
  implicit val formatExtendedAPIDefinition: OFormat[ExtendedAPIDefinition] = Json.format[ExtendedAPIDefinition]

  val apiVersionReads: Reads[APIVersion] = (
    (JsPath \ "version").read[String] and
      (JsPath \ "status").read[APIStatus.APIStatus] and
      (JsPath \ "access").readNullable[APIAccess] and
      (JsPath \ "endpoints").read[List[Endpoint]] and
      (JsPath \ "endpointsEnabled").readNullable[Boolean] and
      (JsPath \ "awsRequestId").readNullable[String] and
      (JsPath \ "versionSource").readNullable[ApiVersionSource].map(_.fold[ApiVersionSource](UNKNOWN)(identity))
  )(APIVersion.apply _)

  val apiVersionWrites: OWrites[APIVersion] = Json.writes[APIVersion]
  implicit val formatApiVersion             = OFormat[APIVersion](apiVersionReads, apiVersionWrites)

  implicit val formatAPIDefinition: OFormat[APIDefinition] = Json.format[APIDefinition]

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
