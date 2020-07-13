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

import org.raml.v2.api.model.v10.common.{Annotable => RamlAnnotable}
import org.raml.v2.api.model.v10.datamodel.{TypeInstance => RamlTypeInstance}

final class AnnotationExtension(val context: RamlAnnotable) extends AnyVal {
    def hasAnnotation(names: String*): Boolean = getAnnotation(context, names: _*).isDefined

    def annotation(names: String*): Option[String] = getAnnotation(context, names: _*).filter(_.nonEmpty)

    private def getAnnotation(context: RamlAnnotable, names: String*): Option[String] = {
    val matches = context.annotations.asScala.find { ann =>
      Option(ann.name).exists(stripNamespace(_) == names.head)
    }

    val out = for {
      m <- matches
      annotation = m.structuredValue
    } yield propertyForPath(annotation, names.tail.toList)

    out.flatten.map(_.toString)
  }

  private def stripNamespace(name: String): String = {
    name.replaceFirst("\\(.*\\.", "(")
  }

  private def propertyForPath(annotation: RamlTypeInstance, path: List[AnyRef]): Option[AnyRef] =
    if (annotation.isScalar) scalarValueOf(annotation, path)
    else complexValueOf(annotation, path)

  private def complexValueOf(annotation: RamlTypeInstance, path: List[AnyRef]): Option[AnyRef] =
    if (path.isEmpty) Option(annotation)
    else getProperty(annotation, path.head) match {
      case Some(ti: RamlTypeInstance) => propertyForPath(ti, path.tail)
      case other => other
    }

  private def scalarValueOf(annotation: RamlTypeInstance, path: List[AnyRef]): Option[AnyRef] =
    if (path.nonEmpty) throw new RuntimeException(s"Scalar annotations do not have properties")
    else Option(annotation.value())

  private def getProperty(annotation: RamlTypeInstance, property: AnyRef) =
    annotation
      .properties.asScala
      .find(prop => prop.name == property)
      .map(ti => transformScalars(ti.value))

  private def transformScalars(value: RamlTypeInstance) =
    if (value.isScalar) value.value() else value
}