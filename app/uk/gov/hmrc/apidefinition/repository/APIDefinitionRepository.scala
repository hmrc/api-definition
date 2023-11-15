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

package uk.gov.hmrc.apidefinition.repository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{equal, regex}
import org.mongodb.scala.model.{FindOneAndReplaceOptions, ReturnDocument}

import play.api.Logging
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import uk.gov.hmrc.apidefinition.models.TolerantJsonApiDefinition
import uk.gov.hmrc.apidefinition.utils.IndexHelper.createUniqueBackgroundSingleFieldAscendingIndex

@Singleton
class APIDefinitionRepository @Inject() (mongoComponent: MongoComponent)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[StoredApiDefinition](
      collectionName = "api",
      mongoComponent = mongoComponent,
      domainFormat = TolerantJsonApiDefinition.tolerantFormatApiDefinition,
      indexes = Seq("context", "name", "serviceName", "serviceBaseUrl")
        .map(fieldName => createUniqueBackgroundSingleFieldAscendingIndex(fieldName, s"${fieldName}Index"))
    ) with Logging {

  override lazy val collection: MongoCollection[StoredApiDefinition] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(TolerantJsonApiDefinition.tolerantFormatApiDefinition)
          ),
          DEFAULT_CODEC_REGISTRY
        )
      )

  private def serviceNameSelector(serviceName: ServiceName): Bson = {
    equal("serviceName", Codecs.toBson(serviceName.value))
  }

  def save(apiDefinition: StoredApiDefinition): Future[StoredApiDefinition] = {
    collection.findOneAndReplace(
      serviceNameSelector(apiDefinition.serviceName),
      apiDefinition,
      FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).head()
  }

  def fetchByServiceName(serviceName: ServiceName): Future[Option[StoredApiDefinition]] = {
    logger.info(s"Fetching API $serviceName in mongo")
    collection.find(serviceNameSelector(serviceName)).headOption().map { api =>
      logger.info(s"Retrieved API with service name '$serviceName' in mongo: $api")
      api
    } recover
      logExceptionAndThrow(s"An error occurred while retrieving API with service name '$serviceName' in mongo")
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[StoredApiDefinition]] = {
    collection.find(equal("serviceBaseUrl", Codecs.toBson(serviceBaseUrl))).headOption().map { api =>
      logger.debug(s"Retrieved API with service base url '$serviceBaseUrl' in mongo: $api")
      api
    } recover
      logExceptionAndThrow(s"An error occurred while retrieving API with service base url '$serviceBaseUrl' in mongo")
  }

  def fetchByName(name: String): Future[Option[StoredApiDefinition]] = {
    collection.find(equal("name", Codecs.toBson(name))).headOption().map { api =>
      logger.debug(s"Retrieved API with name '$name' in mongo: $api")
      api
    } recover
      logExceptionAndThrow(s"An error occurred while retrieving API with name '$name' in mongo")
  }

  def fetchByContext(context: ApiContext): Future[Option[StoredApiDefinition]] = {
    collection.find(equal("context", Codecs.toBson(context.value))).headOption().map { api =>
      logger.debug(s"Retrieved API with context '${context.value}' in mongo: $api")
      api
    } recover
      logExceptionAndThrow(s"An error occurred while retrieving API with context '${context.value}' in mongo")
  }

  def fetchAll(): Future[Seq[StoredApiDefinition]] = {
    collection.find().toFuture()
  }

  def fetchAllByTopLevelContext(topLevelContext: ApiContext): Future[Seq[StoredApiDefinition]] = {
    collection.find(regex("context", f"^${topLevelContext.value}\\/.*$$")).toFuture()
  }

  def delete(serviceName: ServiceName): Future[Unit]                                       = {
    collection.deleteOne(serviceNameSelector(serviceName))
      .toFuture()
      .map(_ => logger.info(s"API with service name '$serviceName' has been deleted successfully"))
  }

  private def logExceptionAndThrow[U](errorMessage: String): PartialFunction[Throwable, U] = {
    case NonFatal(e) =>
      logger.error(errorMessage, e)
      throw e
  }
}
