/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.definition

import play.api.http.Status.OK

class AppHealthSpec extends ComponentSpec {

  "the application" when {
    "the health check endpoint is called" should {
      "respond with 200 OK" in {
        val response = get("/ping/ping")
        response.status shouldBe OK
      }
    }
  }
}
