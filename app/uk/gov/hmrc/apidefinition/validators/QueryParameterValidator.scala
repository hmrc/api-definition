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

package uk.gov.hmrc.apidefinition.validators

import scala.util.matching.Regex

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

object QueryParameterValidator extends Validator[QueryParameter] {

  private val queryParameterNameRegex: Regex = "^[a-zA-Z0-9_\\-]+$".r

  def validate(queryParameter: QueryParameter): HMRCValidatedNel[QueryParameter] = {
    import cats.implicits._
    queryParameter.valid
      .ensure("Field 'queryParameters.name' is required")(_.name.nonBlank)
      .ensure(s"Field 'queryParameters.name' with value '${queryParameter.name}' should match regular expression '$queryParameterNameRegex'")(_.name.matches(queryParameterNameRegex))
      .toValidatedNel
  }
}
