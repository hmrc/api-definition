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

package uk.gov.hmrc.apidefinition.utils

import scala.util.matching.Regex

object RegexHelper {

  private val pathParametersPattern = "\\{([^{}]+?)\\}".r

  private def removeCurlyBrackets(): Regex.Match => String = {
    _.toString.drop(1).dropRight(1)
  }

  def extractPathParameters(url: String): Seq[String] = {
    pathParametersPattern.findAllMatchIn(url).map(removeCurlyBrackets()).toSeq
  }

}
