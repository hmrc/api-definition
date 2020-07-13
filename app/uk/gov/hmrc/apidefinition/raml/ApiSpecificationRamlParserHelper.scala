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

package uk.gov.hmrc.apidefinition.raml

import scala.collection.JavaConverters._

import org.raml.v2.api.model.v10.resources.{Resource => RamlResource}

import uk.gov.hmrc.apidefinition.models.apispecification.ExampleSpec
import org.raml.v2.api.model.v10.datamodel.{ExampleSpec => RamlExampleSpec}
import org.raml.v2.api.model.v10.datamodel.{TypeDeclaration => RamlTypeDeclaration}
import org.raml.v2.api.model.v10.datamodel.{StringTypeDeclaration => RamlStringTypeDeclaration}
import uk.gov.hmrc.apidefinition.models.apispecification.TypeDeclaration
import uk.gov.hmrc.apidefinition.models.apispecification.ResourcesAndGroups
import uk.gov.hmrc.apidefinition.models.apispecification.Group
import uk.gov.hmrc.apidefinition.models.apispecification.Resource
import uk.gov.hmrc.apidefinition.models.apispecification.Method

object ApiSpecificationRamlParserHelper {
  import uk.gov.hmrc.apidefinition.raml.RamlSyntax._

  def toExampleSpec(example : RamlExampleSpec) : ExampleSpec = {

    val description: Option[String] = {
      example.structuredValue.property("description", "value")
    }

    val documentation: Option[String] = {
      example.annotation("(documentation)")
    }

    val code: Option[String] = {
      example.structuredValue.property("value", "code")
      .orElse(example.structuredValue.property("code"))
    }

    val value: Option[String] = {
      example.structuredValue.property("value")
      .orElse(SafeValue(example))
    }

    ExampleSpec(description, documentation, code, value)
  }

  def toTypeDeclaration(td: RamlTypeDeclaration): TypeDeclaration = {
    val examples =
      if(td.example != null)
        List(ApiSpecificationRamlParserHelper.toExampleSpec(td.example))
      else
        td.examples.asScala.toList.map(ApiSpecificationRamlParserHelper.toExampleSpec)

    val enumValues = td match {
      case t: RamlStringTypeDeclaration => t.enumValues().asScala.toList
      case _                            => List()
    }

    val patterns = td match {
      case t: RamlStringTypeDeclaration => Some(t.pattern())
      case _                            => None
    }

    TypeDeclaration(
      td.name,
      SafeValueAsString(td.displayName),
      td.`type`,
      td.required,
      SafeValue(td.description),
      examples,
      enumValues,
      patterns
    )
  }


  def toResourcesAndGroups(ramlResource: RamlResource): ResourcesAndGroups = {
    val childNodes: List[ResourcesAndGroups] = ramlResource.resources().asScala.toList.map(toResourcesAndGroups)

    val children = childNodes.map(_.resource)

    val group = if (ramlResource.hasAnnotation("(group)")) {
        val groupName = ramlResource.annotation("(group)", "name").getOrElse("")
        val groupDesc = ramlResource.annotation("(group)", "description").getOrElse("")
        Some(Group(groupName, groupDesc))
    }
    else {
      None
    }

    val resource = Resource(
      resourcePath = ramlResource.resourcePath,
      methods = methodsForResource(ramlResource),
      relativeUri = ramlResource.relativeUri.value,
      uriParameters = ramlResource.uriParameters.asScala.toList.map(ApiSpecificationRamlParserHelper.toTypeDeclaration),
      displayName = ramlResource.displayName.value,
      children = children
    )

    val thisMap: ResourcesAndGroups.GroupMap          = group.fold(ResourcesAndGroups.emptyGroupMap)(g => Map(resource -> g) )
    val childMaps: List[ResourcesAndGroups.GroupMap]  = childNodes.map(_.groupMap)

    ResourcesAndGroups(resource, ResourcesAndGroups.flatten(thisMap, childMaps))
  }

  private def methodsForResource(resource: RamlResource): List[Method] = {
    val correctOrder = Map("get" -> 0, "post" -> 1, "put" -> 2, "delete" -> 3, "head" -> 4, "patch" -> 5, "options" -> 6)

    resource.methods.asScala.toList.sortWith { (left, right) =>
      (for {
        l <- correctOrder.get(left.method)
        r <- correctOrder.get(right.method)
      } yield l < r).getOrElse(false)
    }
    .map(m => Method(m))
  }
}
