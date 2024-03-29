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

package uk.gov.hmrc.apidefinition.raml

object SafeValue {
  // Handle nulls from RAML
  // Convert nulls and empty strings to Option.None

  def apply(nullableString: String): Option[String] = {
    Option(nullableString).filter(_.nonEmpty)
  }

  // scalastyle:off structural.type
  def apply(nullableObj: { def value(): String }): Option[String] = {
    Option(nullableObj).flatMap(obj => Option(obj.value())).filter(_.nonEmpty)
  }
  // scalastyle:on structural.type
}

object SafeValueAsString {
  // Handle nulls from RAML
  def apply(nullableString: String): String = SafeValue(nullableString).getOrElse("")

  // scalastyle:off structural.type
  def apply(nullableObj: { def value(): String }): String = SafeValue(nullableObj).getOrElse("")
  // scalastyle:on structural.type
}
