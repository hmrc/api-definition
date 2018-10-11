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

import cats.data.ValidatedNel
import cats.implicits._
import uk.gov.hmrc.apidefinition.models.APIDefinition

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.matching.Regex

trait Validator[T] {

  type HMRCValidated[A] = ValidatedNel[String, A]
  type ShouldEvaluateToTrue = T => Boolean
  type ConstructError = T => String

  def validateThat(f: ShouldEvaluateToTrue, errFn: ConstructError)(implicit t: T): HMRCValidated[T] = {
    if (f(t)) t.validNel else errFn(t).invalidNel
  }

  def validateField[U](f: U => Boolean, errFn: U => String)(u: U): HMRCValidated[U] = {
    if (f(u)) u.validNel else errFn(u).invalidNel
  }

  def validateAll[U](f: U => HMRCValidated[U])(us: Traversable[U]): HMRCValidated[List[U]] = {
    us.toList.map(u => f(u).map(_ :: Nil)).combineAll
  }

  implicit protected class RegexString(str: String) {
    def matches(r: Regex): Boolean = {
      r.findFirstIn(str).nonEmpty
    }
  }

  def validateFieldNotAlreadyUsed(fetchApi: => Future[Option[APIDefinition]], errorMessage: String)
                                 (implicit t: T, apiDefinition: APIDefinition): Future[HMRCValidated[T]] = {
    fetchApi.map {
      case Some(found: APIDefinition) => found.serviceName != apiDefinition.serviceName
      case _ => false
    }.map(alreadyUsed => validateThat(_ => !alreadyUsed, _ => errorMessage))
  }

}
