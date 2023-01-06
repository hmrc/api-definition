/*
 * Copyright 2023 HM Revenue & Customs
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

import org.raml.v2.api.model.v10.datamodel.{TypeInstance => RamlTypeInstance, TypeInstanceProperty => RamlTypeInstanceProperty}

final class PropertyExtension(val context: RamlTypeInstance) extends AnyVal {

  def property(names: String*): Option[String] = {
    val properties = context.properties.asScala
    (names match {
      case head +: Nil  => {
        properties.find(_.name == head).map(scalarValue)
      }
      case head +: tail => {
        properties.find(_.name == head) match {
          case Some(nestedProperty) =>
            new PropertyExtension(nestedProperty.value).property(tail: _*)
          case _                    => None
        }
      }
    })
  }

  private def scalarValue(property: RamlTypeInstanceProperty): String = {
    if (!property.isArray && property.value.isScalar) {
      property.value.value.toString
    } else ""
  }
}
