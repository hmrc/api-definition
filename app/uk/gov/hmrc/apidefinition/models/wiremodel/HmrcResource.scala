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

case class HmrcResource(resourcePath: String, group: Option[Group], methods: List[HmrcMethod], uriParameters: List[TypeDeclaration2], relativeUri: String, displayName: String, children: List[HmrcResource])

object HmrcResource {
  def recursiveResource(resource: RamlResource): HmrcResource = {
    val children: List[HmrcResource] = resource.resources().asScala.toList.map(recursiveResource)

    val group = if (Annotation.exists(resource, "(group)")) {
        val groupName = Annotation(resource, "(group)", "name")
        val groupDesc = Annotation(resource, "(group)", "description")
        Some(Group(groupName, groupDesc))
    }
    else {
      None
    }

    HmrcResource(
      resourcePath = resource.resourcePath,
      methods = HmrcMethod(resource),
      group = group,
      relativeUri = resource.relativeUri.value,
      uriParameters = resource.uriParameters.asScala.toList.map(TypeDeclaration2.apply),
      displayName = resource.displayName.value,
      children = children
    )
  }
}
