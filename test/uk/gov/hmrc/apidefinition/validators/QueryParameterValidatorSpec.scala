/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalactic.source.Position

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.QueryParameter

class QueryParameterValidatorSpec extends AbstractValidatorSpec {
  def validates(name: String)(implicit pos: Position): Unit = validates(QueryParameterValidator.validate(QueryParameter(name, false)), clue = Some(name))

  def failsToValidateWithNoName(name: String)(implicit pos: Position): Unit =
    failsToValidate(QueryParameterValidator.validate(QueryParameter(name, false)))("Field 'queryParameters.name' is required")

  def failsToValidateWithBadName(name: String)(implicit pos: Position): Unit =
    failsToValidate(QueryParameterValidator.validate(QueryParameter(name, false)))() // TODO startsWith s"Field 'queryParameters.name' with value '$name' should match regular expression")

  "QueryParameterValidator" should {
    "detect an empty name" in {
      failsToValidateWithNoName("")
    }

    "detect a blank name" in {
      failsToValidateWithNoName("   ")
    }

    "detect an invalid name" in {
      failsToValidateWithBadName("abc!")
      failsToValidateWithBadName("abc#")
      failsToValidateWithBadName("abc&")
      failsToValidateWithBadName("abc+")
      failsToValidateWithBadName("ab c")
      failsToValidateWithBadName("ab/c")
    }

    "detect a valid name" in {
      validates("abc")
      validates("ab-c")
      validates("a_bc")
      validates("a_12bc")
      validates("a_12bc")
    }
  }
}
