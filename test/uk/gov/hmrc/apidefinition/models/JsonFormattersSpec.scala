/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.models

import uk.gov.hmrc.apidefinition.models.JsonFormatters.{apiAccessWrites, dateTimeReads}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsError, JsNumber, Json}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class JsonFormattersSpec extends AsyncHmrcSpec {

  "JsonFormatters" should {

    "fail to read a DateTime json value if not formatted in ISO 8601" in {
      val timeInMillis = DateTime.now(DateTimeZone.UTC).getMillis
      val dateTime     = JsNumber(timeInMillis)

      val expectedErrorMessage = JsError(s"Unexpected format for DateTime: $dateTime")
      Json.fromJson[DateTime](json = dateTime)(fjs = dateTimeReads) shouldBe expectedErrorMessage
    }

    "write isTrial when it is defined in the API access" in {
      val apiAccess = PrivateAPIAccess(Seq("A", "B"), isTrial = Some(false))
      apiAccessWrites.writes(apiAccess).toString shouldBe """{"type":"PRIVATE","whitelistedApplicationIds":["A","B"],"isTrial":false}"""
    }

    "omit isTrial when it is not defined in the API access" in {
      val apiAccess = PrivateAPIAccess(Seq("A", "B"), isTrial = None)
      apiAccessWrites.writes(apiAccess).toString shouldBe """{"type":"PRIVATE","whitelistedApplicationIds":["A","B"]}"""
    }
  }

}
