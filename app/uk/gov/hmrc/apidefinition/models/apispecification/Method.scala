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

package uk.gov.hmrc.apidefinition.models.apispecification

import org.raml.v2.api.model.v10.methods.{Method => RamlMethod}
import scala.collection.JavaConverters._
import uk.gov.hmrc.apidefinition.raml.RamlSyntax._
import uk.gov.hmrc.apidefinition.raml.{SafeValueAsString, SafeValue}
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParserHelper

case class Method(
  method: String,
  displayName: String,
  body: List[TypeDeclaration],
  headers: List[TypeDeclaration],
  queryParameters: List[TypeDeclaration],
  description: Option[String],
  securedBy: Option[SecurityScheme],
  responses: List[Response],
  sandboxData: Option[String]
)

// TODO: Move me
object Method {

  def apply(ramlMethod: RamlMethod): Method = {
    val queryParameters = ramlMethod.queryParameters.asScala.toList.map(ApiSpecificationRamlParserHelper.toTypeDeclaration)
    val headers = ramlMethod.headers.asScala.toList.map(ApiSpecificationRamlParserHelper.toTypeDeclaration)
    val body = ramlMethod.body.asScala.toList.map(ApiSpecificationRamlParserHelper.toTypeDeclaration)

    def fetchAuthorisation: Option[SecurityScheme] = {
      if (ramlMethod.securedBy().asScala.nonEmpty) {
        ramlMethod.securedBy.get(0).securityScheme.`type` match {
          case "OAuth 2.0" => Some(SecurityScheme("user", ramlMethod.annotation("(scope)")))
          case _ => Some(SecurityScheme("application", None))
        }
      } else {
        None
      }
    }

    def responses: List[Response] = {
      ramlMethod.responses().asScala.toList.map( r => {
        Response(
          code = SafeValueAsString(r.code()),
          body = r.body.asScala.toList.map(ApiSpecificationRamlParserHelper.toTypeDeclaration),
          headers = r.headers().asScala.toList.map(ApiSpecificationRamlParserHelper.toTypeDeclaration),
          description = SafeValue(r.description())
        )
      })
    }

    def sandboxData = ramlMethod.annotation("(sandboxData)")

    Method(
      ramlMethod.method,
      SafeValueAsString(ramlMethod.displayName),
      body,
      headers,
      queryParameters,
      SafeValue(ramlMethod.description),
      fetchAuthorisation,
      responses,
      sandboxData
    )
  }
}
