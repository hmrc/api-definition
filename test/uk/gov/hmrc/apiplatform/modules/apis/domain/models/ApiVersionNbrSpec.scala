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

package uk.gov.hmrc.apiplatform.modules.apis.domain.models

import uk.gov.hmrc.apidefinition.utils.HmrcSpec
import scala.util.Random

class ApiVersionNbrSpec extends HmrcSpec {
  
  "ApiVersionNbr" should {
    "sort accordingly" in {
      val sorted = List("1.0", "1.1", "2.0", "2.1", "3")

      val unsorted = Random.shuffle(sorted)

      unsorted.map(ApiVersionNbr(_)).sorted shouldBe sorted.map(ApiVersionNbr(_))
    }
  }
}
