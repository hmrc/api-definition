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

import cats.data.Validated.Invalid
import org.scalactic.source.Position

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.validators.BaseValidator.HMRCValidatedNel

class AbstractValidatorSpec extends AsyncHmrcSpec {

  def validates[T](test: => HMRCValidatedNel[T], numberOfErrors: Int = 1, clue: Option[String] = None)(implicit pos: Position): Unit = {
    def testIt() =
      test match {
        case Invalid(errs) => fail()
        case _             =>
      }
    clue.fold(testIt())(clue => withClue(clue) { testIt() })
  }

  def failsToValidate[T](
      test: => HMRCValidatedNel[T],
      numberOfErrors: Int = 1,
      clue: Option[String] = None
    )(
      expectedErrors: String*
    )(implicit pos: Position
    ): Unit = {
    def testIt() =
      test match {
        case Invalid(errs) =>
          errs.size shouldBe numberOfErrors
          expectedErrors.size match {
            case 0 => errs
            case _ => errs.toList should contain theSameElementsAs expectedErrors
          }
        case _             => fail()
      }
    clue.fold(testIt())(clue => withClue(clue) { testIt() })
  }
}
