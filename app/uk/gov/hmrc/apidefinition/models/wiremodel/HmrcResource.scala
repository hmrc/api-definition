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

case class HmrcResource(resourcePath: String, group: Option[Group], methods: List[HmrcMethod], uriParameters: List[TypeDeclaration], relativeUri: String, displayName: String, children: List[HmrcResource])

object HmrcResource {
  def recursiveResource(resource: RamlResource): HmrcResource = {
    val children: List[HmrcResource] = resource.resources().asScala.toList.map(recursiveResource)

    val group = if (resource.hasAnnotation("(group)")) {
        val groupName = resource.annotation("(group)", "name").getOrElse("")
        val groupDesc = resource.annotation("(group)", "description").getOrElse("")
        Some(Group(groupName, groupDesc))
    }
    else {
      None
    }

    HmrcResource(
      resourcePath = resource.resourcePath,
      methods = methodsForResource(resource),
      group = group,
      relativeUri = resource.relativeUri.value,
      uriParameters = resource.uriParameters.asScala.toList.map(TypeDeclaration.apply),
      displayName = resource.displayName.value,
      children = children
    )
  }

  private def methodsForResource(resource: RamlResource): List[HmrcMethod] = {
    val correctOrder = Map("get" -> 0, "post" -> 1, "put" -> 2, "delete" -> 3, "head" -> 4, "patch" -> 5, "options" -> 6)

    resource.methods.asScala.toList.sortWith { (left, right) =>
      (for {
        l <- correctOrder.get(left.method)
        r <- correctOrder.get(right.method)
      } yield l < r).getOrElse(false)
    }
    .map(m => HmrcMethod(m))
  }
}
