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

import java.time.Instant

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment

case class DisplayApiEvent(
    id: ApiEventId,
    serviceName: ServiceName,
    eventDateTime: Instant,
    eventType: String,
    metaData: List[String],
    environment: Option[Environment]
  )

object DisplayApiEvent {
  implicit val format: OFormat[DisplayApiEvent] = Json.format[DisplayApiEvent]

  def apply(evt: ApiEvent): DisplayApiEvent = {
    val (eventType, metaData) = evt.asMetaData()

    DisplayApiEvent(evt.id, evt.serviceName, evt.eventDateTime, eventType, metaData, None)
  }
}
