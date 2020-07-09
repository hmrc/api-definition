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
import RamlSyntax._

case class Resource(resourcePath: String, methods: List[Method], uriParameters: List[TypeDeclaration], relativeUri: String, displayName: String, children: List[Resource])

/*
1
1.1 A
1.2

2
3   B
*/

object Output {
  type GroupMap = Map[Resource,Group]

  val emptyGroupMap: GroupMap = Map.empty

  def flatten(gm: GroupMap, gms: List[GroupMap]): GroupMap = gms.fold(gm)( (l,r) => l ++ r)

  def flatten(gms: List[GroupMap]): GroupMap = flatten(emptyGroupMap, gms)
}

case class Output(resource: Resource, groupMap: Output.GroupMap)


object Resource {
  def process(ramlResource: RamlResource): Output = {
    val childNodes: List[Output] = ramlResource.resources().asScala.toList.map(process)

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
      uriParameters = ramlResource.uriParameters.asScala.toList.map(TypeDeclaration.apply),
      displayName = ramlResource.displayName.value,
      children = children
    )

    val thisMap: Output.GroupMap          = group.fold(Output.emptyGroupMap)(g => Map(resource -> g) )
    val childMaps: List[Output.GroupMap]  = childNodes.map(_.groupMap)

    Output(resource, Output.flatten(thisMap, childMaps))
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
