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

import org.raml.v2.api.model.v10.datamodel.{ExampleSpec, TypeDeclaration}

import scala.collection.JavaConverters._
import org.raml.v2.api.model.v10.datamodel.{StringTypeDeclaration => RamlStringTypeDeclaration}

case class DocumentationItem(title: String, content: String)

case class SecurityScheme(`type`: String, scope: Option[String])

case class HmrcResponse(
  code: String,
  body: List[TypeDeclaration2],
  headers: List[TypeDeclaration2],
  description: Option[String])


case class Group(name: String, description: String)


//TODO: Change description from MarkDown and example from ExampleSpec
case class TypeDeclaration2(
  name: String,
  displayName: String,
  `type`: String,
  required: Boolean,
  description: Option[String],
  examples: List[HmrcExampleSpec],
  enumValues: List[String],
  pattern: Option[String]){
    val example : Option[HmrcExampleSpec] = examples.headOption
  }

object TypeDeclaration2 {
  def apply(td: TypeDeclaration): TypeDeclaration2 = {
    val examples =
      if(td.example != null)
        List(HmrcExampleSpec(td.example))
      else
        td.examples.asScala.toList.map(HmrcExampleSpec.apply)

    val enumValues = td match {
      case t: RamlStringTypeDeclaration => t.enumValues().asScala.toList
      case _                        => List()
    }

    val patterns = td match {
      case t: RamlStringTypeDeclaration => Some(t.pattern())
      case _                        => None
    }

    TypeDeclaration2(
      td.name,
      DefaultToEmptyValue(td.displayName),
      td.`type`,
      td.required,
      Option(td.description).map(_.value()),
      examples,
      enumValues,
      patterns
    )
  }
}

case class HmrcExampleSpec(
  description: Option[String],
  documentation: Option[String],
  code: Option[String],
  value: Option[String]
)

object HmrcExampleSpec {
  def apply(example : ExampleSpec) : HmrcExampleSpec = {

    val description: Option[String] = {
      FindProperty(example.structuredValue, "description", "value")
    }

    val documentation: Option[String] = {
      if (Annotation.exists(example, "(documentation)")) {
        Option(Annotation(example, "(documentation)"))
      } else {
        None
      }
    }

    val code: Option[String] = {
      FindProperty(example.structuredValue, "value", "code")
        .orElse(FindProperty(example.structuredValue, "code"))
    }

    val value = {
      println(example.structuredValue().value())
      SafeValue(FindProperty(example.structuredValue, "value"))
        .orElse(SafeValue(example))
    }

    HmrcExampleSpec(description, documentation, code, value)
  }
}

case class WireModel (
  title: String,
  version: String,
  deprecationMessage: Option[String],
  documentationItems: List[DocumentationItem],
  resourceGroups: List[HmrcResourceGroup],
  types: List[TypeDeclaration2],
  isFieldOptionalityKnown: Boolean
)

object WireModel {
  def apply(raml: RAML.RAML) : WireModel = {

    def title: String = DefaultToEmptyValue(raml.title)

    def version: String = raml.version.value

    def deprecationMessage: Option[String] = Annotation.optional(raml, "(deprecationMessage)")

    def documentationItems: List[DocumentationItem] =
      raml.documentation.asScala.toList.map(item => DocumentationItem(
        DefaultToEmptyValue(item.title), DefaultToEmptyValue(item.content)
      ))

    def resources: List[HmrcResource] = raml.resources.asScala.toList.map(HmrcResource.recursiveResource)

    def resourceGroups: List[HmrcResourceGroup] = GroupedResources(resources).toList

    def types: List[TypeDeclaration2] = (raml.types.asScala.toList ++ raml.uses.asScala.flatMap(_.types.asScala)).map(TypeDeclaration2.apply)

    def isFieldOptionalityKnown: Boolean = !Annotation.exists(raml, "(fieldOptionalityUnknown)")

    WireModel(
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

// TODO: Add some tests
object SafeValue {
  //Convert nulls and empty strings to Option.None
  def apply(v: String): Option[String] = apply(Option(v))
  def apply(v: {def value(): String}): Option[String] = apply(Option(v).map(_.value()))
  def apply(v: Option[String]): Option[String] = v.filter(_.nonEmpty)
}

object DefaultToEmptyValue {
  def apply(v: {def value(): String}): String = SafeValue(v.value()).getOrElse("")
}

