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

sealed trait ApiVersionSource {
  def asText: String
}

object ApiVersionSource {

  case object RAML extends ApiVersionSource {
    val asText = "RAML"
  }

  case object OAS  extends ApiVersionSource {
    val asText = "OAS"
  }

  case object UNKNOWN extends ApiVersionSource {
    val asText = "UNKNOWN"
  }

  import cats.implicits._

  def apply(text: String): Option[ApiVersionSource] = text.toUpperCase match {
    case "RAML" => ApiVersionSource.RAML.some
    case "OAS" => ApiVersionSource.OAS.some
    case "UNKNOWN" => ApiVersionSource.UNKNOWN.some
    case _ => None
  }

  def unsafeApply(text: String): ApiVersionSource = {
    apply(text).getOrElse(throw new RuntimeException(s"$text is not a valid api version source"))
  }

  import play.api.libs.json._
  
  implicit val apiVersionSourceJF: Format[ApiVersionSource] = new Format[ApiVersionSource] {

    def reads(json: JsValue): JsResult[ApiVersionSource] = json match {
        case JsString(text)         => apply(text).fold[JsResult[ApiVersionSource]]{JsError(s"text is not a valid api version source")}(JsSuccess(_))
      case e                        => JsError(s"Cannot parse source value from '$e'")
    }

    def writes(foo: ApiVersionSource): JsValue = {
      JsString(foo.asText)
    }
  }

}