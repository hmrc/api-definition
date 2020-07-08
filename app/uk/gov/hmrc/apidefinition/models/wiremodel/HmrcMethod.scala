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

package uk.gov.hmrc.apidefinition.models.wiremodel

import org.raml.v2.api.model.v10.resources.{Resource => RamlResource}
import org.raml.v2.api.model.v10.methods.{Method => RamlMethod}
import scala.collection.JavaConverters._
import RamlSyntax._

case class HmrcMethod(
  method: String,
  displayName: String,
  body: List[TypeDeclaration],
  headers: List[TypeDeclaration],
  queryParameters: List[TypeDeclaration],
  description: Option[String],
  securedBy: Option[SecurityScheme],
  responses: List[HmrcResponse],
  sandboxData: Option[String]
)

object HmrcMethod {

  def apply(method: RamlMethod): HmrcMethod = {
    val queryParameters = method.queryParameters.asScala.toList.map(TypeDeclaration.apply)
    val headers = method.headers.asScala.toList.map(TypeDeclaration.apply)
    val body = method.body.asScala.toList.map(TypeDeclaration.apply)


    def fetchAuthorisation: Option[SecurityScheme] = {
      if (method.securedBy().asScala.nonEmpty) {
        method.securedBy.get(0).securityScheme.`type` match {
          case "OAuth 2.0" => Some(SecurityScheme("user", method.annotation("(scope)")))
          case _ => Some(SecurityScheme("application", None))
        }
      } else {
        None
      }
    }

    def responses: List[HmrcResponse] = {
      method.responses().asScala.toList.map( r => {
        HmrcResponse(
          code = SafeValueAsString(r.code()),
          body = r.body.asScala.toList.map(TypeDeclaration.apply),
          headers = r.headers().asScala.toList.map(TypeDeclaration.apply),
          description = SafeValue(r.description())
        )
      })
    }

    def sandboxData = method.annotation("(sandboxData)")

    HmrcMethod(
      method.method,
      SafeValueAsString(method.displayName),
      body,
      headers,
      queryParameters,
      SafeValue(method.description),
      fetchAuthorisation,
      responses,
      sandboxData
    )
  }
}
