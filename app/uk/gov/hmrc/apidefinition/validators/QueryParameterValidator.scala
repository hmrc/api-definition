/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.validators

import uk.gov.hmrc.apidefinition.models.Parameter
import uk.gov.hmrc.apidefinition.validators.ApiDefinitionValidator._

import scala.util.matching.Regex

object QueryParameterValidator extends Validator[Parameter] {

  private val queryParameterNameRegex: Regex = "^[a-zA-Z0-9_\\-]+$".r

  def validate(errorContext: String)(implicit queryParameter: Parameter): HMRCValidated[Parameter] = {
    validateThat(_.name.nonEmpty, _ => s"Field 'queryParameters.name' is required $errorContext").andThen {
      validateField(_.name.matches(queryParameterNameRegex),
        _ => s"Field 'queryParameters.name' should match regular expression '$queryParameterNameRegex' $errorContext")
    }
  }
}
