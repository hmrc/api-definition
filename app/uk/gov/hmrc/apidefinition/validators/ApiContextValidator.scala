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

import java.nio.file.{Path, Paths}
import scala.util.matching.Regex

import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

object ApiContextValidator extends Validator[ApiContext] {

  private val ValidTopLevelContexts: Set[ApiContext] =
    Set("agents", "customs", "individuals", "mobile", "obligations", "organisations", "test", "payments", "misc", "accounts")
      .map(ApiContext(_))

  private val formattedTopLevelContexts = ValidTopLevelContexts.map(_.value).toList.sorted.mkString("'", "', '", "'")

  private val contextRegex: Regex = """^[a-z]+[a-z\/\-]{4,}$""".r

  def validateForExistingAPI(skipContextValidation: Boolean)(apiContext: ApiContext): HMRCValidatedNel[ApiContext] = {
    validateContext(skipContextValidation)(apiContext)
      .map { case _ => apiContext }
      .leftMap(_.map(s => s"$apiContext - $s"))
  }

  def validateForNewAPI(skipContextValidation: Boolean)(apiContext: ApiContext, otherContextsInTopLevel: List[ApiContext]): HMRCValidatedNel[ApiContext] = {
    (
      validateTopLevelContext(skipContextValidation)(apiContext),
      validateContextHasAtLeastTwoSegments(skipContextValidation)(apiContext),
      validateContextDoesNotOverlapExistingAPI(apiContext, otherContextsInTopLevel)
    )
      .mapN { case _ => apiContext }
      .leftMap(_.map(s => s"$apiContext - $s"))
  }

  def validateTopLevelContext(skipContextValidation: Boolean)(apiContext: ApiContext): HMRCValidatedNel[ApiContext] = {
    apiContext.valid
      .ensure(s"Field 'context' must start with one of $formattedTopLevelContexts")(c => skipContextValidation || ValidTopLevelContexts.contains(c.topLevelContext()))
      .toValidatedNel
  }

  def validateContext(skipContextValidation: Boolean)(apiContext: ApiContext): HMRCValidatedNel[ApiContext] = {
    apiContext.valid
      .ensure("Field 'context' should not have empty path segments")(c => !c.value.contains("//"))
      .ensure(s"Field 'context' should match regular expression '$contextRegex'")(c => skipContextValidation || c.value.matches(contextRegex))
      .toValidatedNel
  }

  def validateContextHasAtLeastTwoSegments(skipContextValidation: Boolean)(apiContext: ApiContext): HMRCValidatedNel[ApiContext] =
    apiContext.valid
      .ensure("Field 'context' must have at least two segments")(c => skipContextValidation || c.segments().length > 1)
      .toValidatedNel

  def validateContextDoesNotOverlapExistingAPI(apiContext: ApiContext, otherContextsInTopLevel: List[ApiContext]): HMRCValidatedNel[ApiContext] = {
    def overlap(firstPath: Path, secondPath: Path): Boolean = {
      firstPath.startsWith(secondPath) || secondPath.startsWith(firstPath)
    }

    def validateNoOverlap(otherContext: ApiContext): HMRCValidatedNel[ApiContext] = {
      otherContext.valid
        .ensure(s"Field 'context' overlaps with '$otherContext'")(_ => !overlap(Paths.get(otherContext.value), Paths.get(apiContext.value)))
        .toValidatedNel
    }

    otherContextsInTopLevel
      .map(validateNoOverlap(_).map(_ => List.empty))
      .combineAll
      .map(_ => apiContext)
  }

}
