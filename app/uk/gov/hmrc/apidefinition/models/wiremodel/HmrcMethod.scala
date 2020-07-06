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
import scala.collection.JavaConverters._
import org.raml.v2.api.model.v10.methods.{Method => RamlMethod}

case class HmrcMethod(
  method: String,
  displayName: String,
  body: List[TypeDeclaration2],
  headers: List[TypeDeclaration2],
  queryParameters: List[TypeDeclaration2],
  description: Option[String],
  securedBy: Option[SecurityScheme],
  responses: List[HmrcResponse],
  sandboxData: Option[String]
)

object HmrcMethod {

private val correctOrder = Map(
    "get" -> 0, "post" -> 1, "put" -> 2, "delete" -> 3,
    "head" -> 4, "patch" -> 5, "options" -> 6
  )

  def apply(method: RamlMethod): HmrcMethod = {
    val queryParameters = method.queryParameters.asScala.toList.map(TypeDeclaration2.apply)
    val headers = method.headers.asScala.toList.map(TypeDeclaration2.apply)
    val body = method.body.asScala.toList.map(TypeDeclaration2.apply)


    def fetchAuthorisation: Option[SecurityScheme] = {
      if (method.securedBy().asScala.nonEmpty) {
        method.securedBy.get(0).securityScheme.`type` match {
          case "OAuth 2.0" => Some(SecurityScheme("user", Some(Annotation(method, "(scope)"))))
          case _ => Some(SecurityScheme("application", None))
        }
      } else {
        None
      }
    }

    def responses: List[HmrcResponse] = {
      method.responses().asScala.toList.map(r => {
        HmrcResponse(
          code = DefaultToEmptyValue(r.code()),
          body = r.body.asScala.toList.map(TypeDeclaration2.apply),
          headers = r.headers().asScala.toList.map(TypeDeclaration2.apply),
          description = Option(r.description()).map(_.value())
        )
      })
    }

    def sandboxData = Annotation.optional(method, "(sandboxData)")

    HmrcMethod(
      method.method,
      DefaultToEmptyValue(method.displayName),
      body,
      headers,
      queryParameters,
      SafeValue(method.description),
      fetchAuthorisation,
      responses,
      sandboxData
    )
  }

  def apply(resource: RamlResource): List[HmrcMethod] =
    resource.methods.asScala.toList.sortWith { (left, right) =>
      (for {
        l <- correctOrder.get(left.method)
        r <- correctOrder.get(right.method)
      } yield l < r).getOrElse(false)
    }
    .map(m => HmrcMethod.apply(m))

  def apply(resource: HmrcResource): List[HmrcMethod] =
    resource.methods.sortWith { (left, right) =>
      (for {
        l <- correctOrder.get(left.method)
        r <- correctOrder.get(right.method)
      } yield l < r).getOrElse(false)
    }
}
