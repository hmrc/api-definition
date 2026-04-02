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

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._

trait BaseValidator {

  implicit protected class StringSyntax(in: String) {
    @inline def nonBlank: Boolean = !in.isBlank
  }

  implicit protected class RegexString(str: String) {

    def matches(r: Regex): Boolean = {
      r.findFirstIn(str).nonEmpty
    }
  }

  implicit protected class NelStringSyntax(str: String) {

    def nel: NonEmptyList[String] = {
      NonEmptyList.one(str)
    }
  }

  val valid    = ().valid[String]
  val validNel = ().validNel[String]

  type HMRCValidatedNel[A] = ValidatedNel[String, A]
}

object BaseValidator extends BaseValidator

trait Validator[T] extends BaseValidator {}
