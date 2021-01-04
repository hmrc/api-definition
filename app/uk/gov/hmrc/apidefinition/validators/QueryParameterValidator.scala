/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidefinition.models.Parameter

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

@Singleton
class QueryParameterValidator @Inject()(implicit override val ec: ExecutionContext) extends Validator[Parameter] {

  private val queryParameterNameRegex: Regex = "^[a-zA-Z0-9_\\-]+$".r

  def validate(errorContext: String)(implicit queryParameter: Parameter): HMRCValidated[Parameter] = {
    validateThat(_.name.nonEmpty, _ => s"Field 'queryParameters.name' is required $errorContext").andThen {
      validateField(_.name.matches(queryParameterNameRegex),
        q => s"Field 'queryParameters.name' with value '${q.name}' should match regular expression '$queryParameterNameRegex' $errorContext")
    }
  }
}
