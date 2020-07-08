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

object GroupedResources {
  def apply(resources: List[Resource]): List[ResourceGroup] = {
    def flatten(resources: List[Resource], acc: List[Resource]): List[Resource] = {
      resources match {
        case Nil => acc
        case head :: tail =>
          // TODO - not efficient to right concat
          flatten(tail, flatten(head.children, head :: acc))
      }
    }

    def group(resources: List[Resource], currentGroup: ResourceGroup = ResourceGroup(), groups: List[ResourceGroup] = Nil): List[ResourceGroup] = {
      resources match {
        case head :: tail => {
          if (head.group.isDefined) {
            group(tail, ResourceGroup(head.group.map(_.name), head.group.map(_.description), List(head)), groups :+ currentGroup)
          } else {
            group(tail, currentGroup + head, groups)
          }
        }
        case _ => groups :+ currentGroup
      }
    }

    group(flatten(resources, Nil).reverse).filterNot(_.resources.length < 1)
  }
}
