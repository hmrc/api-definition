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
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccessType, ApiStatus, Endpoint, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.play.json.Union

import uk.gov.hmrc.apidefinition.models.ApiEvent.MetaData
import uk.gov.hmrc.apidefinition.models.ApiEvents.*

final case class ApiEventId(value: UUID) extends AnyVal

object ApiEventId {
  def random: ApiEventId = ApiEventId(UUID.randomUUID())

  given Format[ApiEventId] = Json.valueFormat[ApiEventId]
}

sealed trait ApiEvent {
  def id: ApiEventId
  def apiName: String
  def serviceName: ServiceName
  def eventDateTime: Instant

  def asMetaData(): MetaData
}

object ApiEvent {
  type MetaData = (String, List[String])

  given Ordering[ApiEvent] = new Ordering[ApiEvent]() {

    override def compare(x: ApiEvent, y: ApiEvent): Int =
      y.eventDateTime.compareTo(x.eventDateTime)
  }
}

object ApiEvents {

  case class ApiCreated(id: ApiEventId, apiName: String, serviceName: ServiceName, eventDateTime: Instant) extends ApiEvent {
    override def asMetaData(): MetaData = ("Api Created", List())
  }

  case class NewApiVersion(id: ApiEventId, apiName: String, serviceName: ServiceName, eventDateTime: Instant, apiStatus: ApiStatus, versionNbr: ApiVersionNbr) extends ApiEvent {
    override def asMetaData(): MetaData = ("New Api Version Published", List(s"Version: $versionNbr", s"Api Status: ${apiStatus}"))
  }

  case class ApiVersionStatusChange(
      id: ApiEventId,
      apiName: String,
      serviceName: ServiceName,
      eventDateTime: Instant,
      oldApiStatus: ApiStatus,
      newApiStatus: ApiStatus,
      versionNbr: ApiVersionNbr
    ) extends ApiEvent {

    override def asMetaData(): MetaData =
      ("Api Version Status Change", List(s"Version: $versionNbr", s"Old Api Status: ${oldApiStatus}", s"New Api Status: ${newApiStatus}"))
  }

  case class ApiVersionAccessChange(
      id: ApiEventId,
      apiName: String,
      serviceName: ServiceName,
      eventDateTime: Instant,
      oldApiAccess: ApiAccessType,
      newApiAccess: ApiAccessType,
      versionNbr: ApiVersionNbr
    ) extends ApiEvent {

    private def accessDisplay(access: ApiAccessType) = access match {
      case ApiAccessType.Public     => "Public"
      case ApiAccessType.Controlled => "Controlled"
      case ApiAccessType.Internal   => "Internal"
    }

    override def asMetaData(): MetaData =
      ("Api Version Access Change", List(s"Version: $versionNbr", s"Old Api Access: ${accessDisplay(oldApiAccess)}", s"New Api Access: ${accessDisplay(newApiAccess)}"))
  }

  case class ApiVersionEndpointsAdded(
      id: ApiEventId,
      apiName: String,
      serviceName: ServiceName,
      eventDateTime: Instant,
      endpoints: List[Endpoint],
      versionNbr: ApiVersionNbr
    ) extends ApiEvent {

    override def asMetaData(): MetaData =
      ("Api Version Endpoints Added", List(s"Version: $versionNbr") ++ endpoints.map(e => s"Endpoint: ${e.method.toString.toUpperCase()}: ${e.uriPattern}"))
  }

  case class ApiVersionEndpointsRemoved(
      id: ApiEventId,
      apiName: String,
      serviceName: ServiceName,
      eventDateTime: Instant,
      endpoints: List[Endpoint],
      versionNbr: ApiVersionNbr
    ) extends ApiEvent {

    override def asMetaData(): MetaData =
      ("Api Version Endpoints Removed", List(s"Version: $versionNbr") ++ endpoints.map(e => s"Endpoint: ${e.method.toString.toUpperCase()}: ${e.uriPattern}"))
  }

  case class ApiPublishedNoChange(id: ApiEventId, apiName: String, serviceName: ServiceName, eventDateTime: Instant) extends ApiEvent {
    override def asMetaData(): MetaData = ("Api Published No Change", List())
  }

}

object ApiEventFormatter {
  given OFormat[ApiCreated]                 = Json.format[ApiCreated]
  given OFormat[NewApiVersion]              = Json.format[NewApiVersion]
  given OFormat[ApiVersionStatusChange]     = Json.format[ApiVersionStatusChange]
  given OFormat[ApiVersionAccessChange]     = Json.format[ApiVersionAccessChange]
  given OFormat[ApiVersionEndpointsAdded]   = Json.format[ApiVersionEndpointsAdded]
  given OFormat[ApiVersionEndpointsRemoved] = Json.format[ApiVersionEndpointsRemoved]
  given OFormat[ApiPublishedNoChange]       = Json.format[ApiPublishedNoChange]

  given OFormat[ApiEvent] = Union.from[ApiEvent]("eventType")
    .and[ApiCreated]("API_CREATED")
    .and[NewApiVersion]("NEW_API_VERSION")
    .and[ApiVersionStatusChange]("API_VERSION_STATUS_CHANGE")
    .and[ApiVersionAccessChange]("API_VERSION_ACCESS_CHANGE")
    .and[ApiVersionEndpointsAdded]("API_VERSION_ENDPOINTS_ADDED")
    .and[ApiVersionEndpointsRemoved]("API_VERSION_ENDPOINTS_REMOVED")
    .and[ApiPublishedNoChange]("API_PUBLISHED_NO_CHANGE")
    .format
}
