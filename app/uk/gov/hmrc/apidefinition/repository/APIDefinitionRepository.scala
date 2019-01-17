/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.utils.IndexHelper.createUniqueBackgroundSingleFieldAscendingIndex
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class APIDefinitionRepository @Inject()(mongo: ReactiveMongoComponent)
  extends ReactiveRepository[APIDefinition, BSONObjectID](
    collectionName = "api",
    mongo = mongo.mongoConnector.db,
    domainFormat = formatAPIDefinition,
    idFormat = ReactiveMongoFormats.objectIdFormats) {

  override def indexes: Seq[Index] = {
    val indexFieldNames: Seq[String] = Seq("context", "name", "serviceName", "serviceBaseUrl")
    indexFieldNames.map( fieldName => createUniqueBackgroundSingleFieldAscendingIndex(fieldName, Some(s"${fieldName}Index")) )
  }

  private def serviceNameSelector(serviceName: String) = {
    Json.obj("serviceName" -> serviceName)
  }

  def save(apiDefinition: APIDefinition): Future[APIDefinition] = {
    collection.find(selector = serviceNameSelector(apiDefinition.serviceName)).one[BSONDocument].flatMap {
      case Some(document) => collection.update(selector = BSONDocument("_id" -> document.get("_id")), update = apiDefinition)
      case _ => collection.insert(document = apiDefinition)
    } map (_ => apiDefinition)
  }

  def fetchByServiceName(serviceName: String): Future[Option[APIDefinition]] = {
    Logger.info(s"Fetching API $serviceName in mongo")
    collection.find(selector = serviceNameSelector(serviceName)).one[APIDefinition].map { api =>
      Logger.info(s"Retrieved API with service name '$serviceName' in mongo: $api")
      api
    } recover {
      case e =>
        Logger.error(s"An error occurred while retrieving API with service name '$serviceName' in mongo", e)
        throw e
    }
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[APIDefinition]] = {
    collection.find(selector = Json.obj("serviceBaseUrl" -> serviceBaseUrl)).one[APIDefinition].map { api =>
      Logger.debug(s"Retrieved API with service base url '$serviceBaseUrl' in mongo: $api")
      api
    } recover {
      case e =>
        Logger.error(s"An error occurred while retrieving API with service base url '$serviceBaseUrl' in mongo", e)
        throw e
    }
  }

  def fetchByName(name: String): Future[Option[APIDefinition]] = {
    collection.find(selector = Json.obj("name" -> name)).one[APIDefinition].map { api =>
      Logger.debug(s"Retrieved API with name '$name' in mongo: $api")
      api
    } recover {
      case e =>
        Logger.error(s"An error occurred while retrieving API with name '$name' in mongo", e)
        throw e
    }
  }

  def fetchByContext(context: String): Future[Option[APIDefinition]] = {
    collection.find(selector = Json.obj("context" -> context)).one[APIDefinition].map { api =>
      Logger.debug(s"Retrieved API with context '$context' in mongo: $api")
      api
    } recover {
      case e =>
        Logger.error(s"An error occurred while retrieving API with context '$context' in mongo", e)
        throw e
    }
  }

  def fetchAll(): Future[Seq[APIDefinition]] = {
    collection.find(selector = Json.obj()).cursor[APIDefinition].collect[Seq]()
  }

  def delete(serviceName: String): Future[Unit] = {
    collection.remove(selector = serviceNameSelector(serviceName))
      .map(_ => Logger.info(s"API with service name '$serviceName' has been deleted successfully"))
  }

}
