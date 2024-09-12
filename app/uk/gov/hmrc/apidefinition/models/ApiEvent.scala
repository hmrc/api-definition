/*
 * Copyright 2024 HM Revenue & Customs
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
import java.util.UUID

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccess, ApiStatus, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apidefinition.models.ApiEvent.MetaData
import uk.gov.hmrc.apidefinition.models.ApiEvents._

final case class EventId(value: UUID) extends AnyVal

object EventId {
  def random: EventId = EventId(UUID.randomUUID())

  implicit val eventIdJf: Format[EventId] = Json.valueFormat[EventId]
}

sealed trait ApiEvent {
  def id: EventId
  def serviceName: ServiceName
  def eventDateTime: Instant

  def asMetaData(): MetaData
}

object ApiEvent {
  type MetaData = (String, List[String])

  implicit val orderEvents: Ordering[ApiEvent] = new Ordering[ApiEvent]() {

    override def compare(x: ApiEvent, y: ApiEvent): Int =
      y.eventDateTime.compareTo(x.eventDateTime)
  }
}

object ApiEvents {

  case class ApiCreated(id: EventId, serviceName: ServiceName, eventDateTime: Instant) extends ApiEvent {
    override def asMetaData(): MetaData = ("Api Created", List())
  }

  case class NewApiVersion(id: EventId, serviceName: ServiceName, eventDateTime: Instant, apiStatus: ApiStatus, versionNbr: ApiVersionNbr) extends ApiEvent {
    override def asMetaData(): MetaData = ("New Api Version Published", List(s"Version: $versionNbr", s"Api Status: ${apiStatus.displayText}"))
  }

  case class ApiVersionStatusChange(id: EventId, serviceName: ServiceName, eventDateTime: Instant, oldApiStatus: ApiStatus, newApiStatus: ApiStatus, versionNbr: ApiVersionNbr)
      extends ApiEvent {

    override def asMetaData(): MetaData =
      ("Api Version Status Change", List(s"Version: $versionNbr", s"Old Api Status: ${oldApiStatus.displayText}", s"New Api Status: ${newApiStatus.displayText}"))
  }

  case class ApiVersionAccessChange(id: EventId, serviceName: ServiceName, eventDateTime: Instant, oldApiAccess: ApiAccess, newApiAccess: ApiAccess, versionNbr: ApiVersionNbr)
      extends ApiEvent {

    override def asMetaData(): MetaData =
      ("Api Version Access Change", List(s"Version: $versionNbr", s"Old Api Access: ${oldApiAccess.displayText}", s"New Api Access: ${newApiAccess.displayText}"))
  }

  case class ApiPublishedNoChange(id: EventId, serviceName: ServiceName, eventDateTime: Instant) extends ApiEvent {
    override def asMetaData(): MetaData = ("Api Published No Change", List())
  }

}

object ApiEventFormatter {
  implicit val apiCreatedFormatter: OFormat[ApiCreated]                         = Json.format[ApiCreated]
  implicit val newApiVersionFormatter: OFormat[NewApiVersion]                   = Json.format[NewApiVersion]
  implicit val apiVersionStatusFormatter: OFormat[ApiVersionStatusChange]       = Json.format[ApiVersionStatusChange]
  implicit val apiVersionAccessChangeFormatter: OFormat[ApiVersionAccessChange] = Json.format[ApiVersionAccessChange]
  implicit val apiPublishedNoChangeFormatter: OFormat[ApiPublishedNoChange]     = Json.format[ApiPublishedNoChange]

  implicit val apiEventsFormats: OFormat[ApiEvent] = Union.from[ApiEvent]("eventType")
    .and[ApiCreated]("API_CREATED")
    .and[NewApiVersion]("NEW_API_VERSION")
    .and[ApiVersionStatusChange]("API_VERSION_STATUS_CHANGE")
    .and[ApiVersionAccessChange]("API_VERSION_ACCESS_CHANGE")
    .and[ApiPublishedNoChange]("API_PUBLISHED_NO_CHANGE")
    .format
}
