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

package uk.gov.hmrc.apidefinition.models

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.StoredApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models._

import uk.gov.hmrc.apidefinition.models.AWSAPIDefinition.awsApiGatewayName
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class AWSAPIDefinitionSpec extends AsyncHmrcSpec {

  "awsApiGatewayName" should {

    "replace '/' in context with '--'" in {

      val apiDefinition = mock[StoredApiDefinition]
      when(apiDefinition.context).thenReturn(ApiContext("my/calendar"))

      awsApiGatewayName(ApiVersionNbr("1.0"), apiDefinition) shouldBe "my--calendar--1.0"
    }

  }

}
