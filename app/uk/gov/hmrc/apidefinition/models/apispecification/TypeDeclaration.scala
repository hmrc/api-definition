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

import org.raml.v2.api.model.v10.datamodel.{TypeDeclaration => RamlTypeDeclaration}
import org.raml.v2.api.model.v10.datamodel.{StringTypeDeclaration => RamlStringTypeDeclaration}
import scala.collection.JavaConverters._
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParserHelper
import uk.gov.hmrc.apidefinition.raml.{SafeValueAsString, SafeValue}

case class TypeDeclaration(
  name: String,
  displayName: String,
  `type`: String,
  required: Boolean,
  description: Option[String],
  examples: List[ExampleSpec],
  enumValues: List[String],
  pattern: Option[String]){
    val example : Option[ExampleSpec] = examples.headOption
  }

// TODO: Move me
object TypeDeclaration {
  def apply(td: RamlTypeDeclaration): TypeDeclaration = {
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
}
