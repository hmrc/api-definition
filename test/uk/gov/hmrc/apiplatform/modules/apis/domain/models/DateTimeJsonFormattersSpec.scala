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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import uk.gov.hmrc.apidefinition.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.common.utils.BaseJsonFormattersSpec

class DateTimeJsonFormattersSpec extends HmrcSpec with BaseJsonFormattersSpec {
  
  import DateTimeJsonFormatters._
  "DateTimeJsonFormatters" should {

    "fail to read a DateTime json value if not formatted in ISO 8601" in {
      val timeInMillis = DateTime.now(DateTimeZone.UTC).getMillis
      val dateTime     = JsNumber(timeInMillis)

      val expectedErrorMessage = JsError(s"Unexpected format for DateTime: $dateTime")
      Json.fromJson[DateTime](json = dateTime)(fjs = DateTimeJsonFormatters.dateTimeReads) shouldBe expectedErrorMessage
    }

    val dateTime = new DateTime(2000,1,2,3,4,5,6).withZone(DateTimeZone.UTC)
    
    "read from Json" in {
      testFromJson[DateTime](""""2000-01-02T03:04:05.006Z"""")(dateTime)
    }

    "write to Json" in {
      Json.toJson[DateTime](dateTime) shouldBe JsString("2000-01-02T03:04:05.006Z")
    }
  }
}
