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

import cats.implicits._
import uk.gov.hmrc.apidefinition.validators.ApiDefinitionValidator.HMRCValidated

object ApiContextValidator extends Validator[String] {

  private val contextRegex: String = "^[a-zA-Z0-9_\\-\\/]+$"

  def validate(errorContext: String)(implicit context: String): HMRCValidated[String] = {
    validateThat(_.nonEmpty, _ => s"Context should not be empty $errorContext").andThen(validateContext(errorContext)(_))
  }

  private def validateContext(errorContext: String)(implicit context: String): HMRCValidated[String] = {
    (
      validateThat(!_.startsWith("/"), _ => s"Context should not start with / $errorContext"),
      validateThat(!_.endsWith("/"), _ => s"Context should not end with / $errorContext"),
      validateThat(!_.contains("//"), _ => s"Context should not have empty path segments $errorContext"),
      validateThat(_.matches(contextRegex), _ => s"Context is invalid $errorContext")
    ).mapN((_,_,_,_) => context)
  }

}
