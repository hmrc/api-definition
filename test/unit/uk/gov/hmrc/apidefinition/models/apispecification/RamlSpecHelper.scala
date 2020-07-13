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

package unit.uk.gov.hmrc.apidefinition.models.apispecification

import uk.gov.hmrc.apidefinition.raml.RAML.RAML
import uk.gov.hmrc.ramltools.loaders.ComprehensiveClasspathRamlLoader

import scala.util.{Failure, Success}

object RamlSpecHelper {
  def loadRaml(filename: String) : RAML = {
    new ComprehensiveClasspathRamlLoader().load(s"test/resources/raml/$filename") match {
      case Failure(exception) => throw exception
      case Success(raml) => raml
    }
  }
}
