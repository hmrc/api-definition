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

object ApiSpecificationFormatters {
  import play.api.libs.json.Json

  implicit val hmrcExampleSpecJF = Json.format[ExampleSpec]
  implicit val typeDeclarationJF = Json.format[TypeDeclaration]

  implicit val securitySchemeJF = Json.format[SecurityScheme]

  implicit val groupJF = Json.format[Group]
  implicit val hmrcResponseJF = Json.format[Response]
  implicit val hmrcMethodJF = Json.format[Method]
  implicit val hmrcResourceJF = Json.format[Resource]

  implicit val documentationItemJF = Json.format[DocumentationItem]
  implicit val hmrcResourceGroupJF = Json.format[ResourceGroup]

  implicit val apiSpecificationJF = Json.format[ApiSpecification]

  implicit val jsonSchemaJF = Json.writes[JsonSchema]
}
