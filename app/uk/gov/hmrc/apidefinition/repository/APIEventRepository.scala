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

package uk.gov.hmrc.apidefinition.repository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model._

import play.api.Logging
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apidefinition.models.ApiEvents._
import uk.gov.hmrc.apidefinition.models.{ApiEvent, ApiEventFormatter}

@Singleton
class APIEventRepository @Inject() (mongoComponent: MongoComponent)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[ApiEvent](
      collectionName = "api-events",
      mongoComponent = mongoComponent,
      domainFormat = ApiEventFormatter.apiEventsFormats,
      indexes = Seq(
        IndexModel(
          ascending("id"),
          IndexOptions()
            .name("id_index")
            .unique(true)
            .background(true)
        ),
        IndexModel(
          ascending("eventType"),
          IndexOptions()
            .name("eventType_index")
            .unique(false)
            .background(true)
        ),
        IndexModel(
          ascending("serviceName"),
          IndexOptions()
            .name("serviceName_index")
            .unique(false)
            .background(true)
        )
      ),
      extraCodecs = Codecs.playFormatCodecsBuilder(ApiEventFormatter.apiEventsFormats)
        .forType[ApiCreated]
        .forType[NewApiVersion]
        .forType[ApiVersionStatusChange]
        .forType[ApiVersionAccessChange]
        .forType[ApiPublishedNoChange]
        .build,
      replaceIndexes = true
    ) with Logging {
  override lazy val requiresTtlIndex = false

  def createEvent(apiEvent: ApiEvent): Future[Boolean] = {
    collection.insertOne(apiEvent).toFuture().map(_.wasAcknowledged())
  }

  def fetchEvents(serviceName: ServiceName): Future[List[ApiEvent]] = {
    collection.find(equal("serviceName", Codecs.toBson(serviceName)))
      .toFuture()
      .map(_.toList)
  }
}