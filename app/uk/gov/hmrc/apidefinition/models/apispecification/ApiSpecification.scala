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

import scala.collection.JavaConverters._
import  RamlSyntax._
import uk.gov.hmrc.apidefinition.raml._

case class DocumentationItem(title: String, content: String)

case class SecurityScheme(`type`: String, scope: Option[String])

case class Response(
  code: String,
  body: List[TypeDeclaration],
  headers: List[TypeDeclaration],
  description: Option[String])

case class Group(name: String, description: String)

case class ApiSpecification (
  title: String,
  version: String,
  deprecationMessage: Option[String],
  documentationItems: List[DocumentationItem],
  resourceGroups: List[ResourceGroup],
  types: List[TypeDeclaration],
  isFieldOptionalityKnown: Boolean
)

// TODO: Move me
object ApiSpecification {
  def apply(raml: RAML.RAML) : ApiSpecification = {

    def title: String = SafeValueAsString(raml.title)

    def version: String = raml.version.value

    def deprecationMessage: Option[String] = raml.annotation("(deprecationMessage)")

    def documentationItems: List[DocumentationItem] =
      raml.documentation.asScala.toList.map(item => DocumentationItem(
        SafeValueAsString(item.title), SafeValueAsString(item.content)
      ))

    def output: List[ResourcesAndGroups] = raml.resources.asScala.toList.map(ApiSpecificationRamlParserHelper.toResourcesAndGroups)

    lazy val resources = output.map(_.resource)

    lazy val groupMap = ResourcesAndGroups.flatten(output.map(_.groupMap))

    def resourceGroups: List[ResourceGroup] = ResourceGroup.generateFrom(resources, groupMap)

    def types: List[TypeDeclaration] = (raml.types.asScala.toList ++ raml.uses.asScala.flatMap(_.types.asScala)).map(ApiSpecificationRamlParserHelper.toTypeDeclaration)

    def isFieldOptionalityKnown: Boolean = !raml.hasAnnotation("(fieldOptionalityUnknown)")

    ApiSpecification(
      title,
      version,
      deprecationMessage,
      documentationItems,
      resourceGroups,
      types,
      isFieldOptionalityKnown
    )
  }
}
