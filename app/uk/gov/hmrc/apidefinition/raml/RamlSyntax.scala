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

import org.raml.v2.api.model.v10.common.{Annotable => RamlAnnotable}
import org.raml.v2.api.model.v10.datamodel.{TypeInstance => RamlTypeInstance}
import uk.gov.hmrc.apidefinition.models.apispecification.AnnotationExtension
import uk.gov.hmrc.apidefinition.raml.PropertyExtension

object RamlSyntax {
  implicit def toAnnotationExtension(context: RamlAnnotable): AnnotationExtension = new AnnotationExtension(context)

  implicit def toPropertyExtension(context: RamlTypeInstance): PropertyExtension = new PropertyExtension(context)
}
