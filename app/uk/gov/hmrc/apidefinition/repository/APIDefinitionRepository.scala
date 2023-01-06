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

import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{FindOneAndReplaceOptions, ReturnDocument}
import org.mongodb.scala.model.Filters.{equal, regex}
import play.api.Logging
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.utils.IndexHelper.createUniqueBackgroundSingleFieldAscendingIndex
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class APIDefinitionRepository @Inject() (mongoComponent: MongoComponent)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[APIDefinition](
      collectionName = "api",
      mongoComponent = mongoComponent,
      domainFormat = formatAPIDefinition,
      indexes = Seq("context", "name", "serviceName", "serviceBaseUrl")
        .map(fieldName => createUniqueBackgroundSingleFieldAscendingIndex(fieldName, s"${fieldName}Index"))
    ) with Logging {

  override lazy val collection: MongoCollection[APIDefinition] =
    CollectionFactory
      .collection(mongoComponent.database, collectionName, domainFormat)
      .withCodecRegistry(
        fromRegistries(
          fromCodecs(
            Codecs.playFormatCodec(domainFormat),
            Codecs.playFormatCodec(formatAPIDefinition)
          ),
          DEFAULT_CODEC_REGISTRY
        )
      )

  private def serviceNameSelector(serviceName: String): Bson = {
    equal("serviceName", Codecs.toBson(serviceName))
  }

  def save(apiDefinition: APIDefinition): Future[APIDefinition] = {
    collection.findOneAndReplace(
      serviceNameSelector(apiDefinition.serviceName),
      apiDefinition,
      FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).head()
  }

  def fetchByServiceName(serviceName: String): Future[Option[APIDefinition]] = {
    logger.info(s"Fetching API $serviceName in mongo")
    collection.find(serviceNameSelector(serviceName)).headOption().map { api =>
      logger.info(s"Retrieved API with service name '$serviceName' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with service name '$serviceName' in mongo", e)
        throw e
    }
  }

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[APIDefinition]] = {
    collection.find(equal("serviceBaseUrl", Codecs.toBson(serviceBaseUrl))).headOption().map { api =>
      logger.debug(s"Retrieved API with service base url '$serviceBaseUrl' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with service base url '$serviceBaseUrl' in mongo", e)
        throw e
    }
  }

  def fetchByName(name: String): Future[Option[APIDefinition]] = {
    collection.find(equal("name", Codecs.toBson(name))).headOption().map { api =>
      logger.debug(s"Retrieved API with name '$name' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with name '$name' in mongo", e)
        throw e
    }
  }

  def fetchByContext(context: String): Future[Option[APIDefinition]] = {
    collection.find(equal("context", Codecs.toBson(context))).headOption().map { api =>
      logger.debug(s"Retrieved API with context '$context' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with context '$context' in mongo", e)
        throw e
    }
  }

  def fetchAll(): Future[Seq[APIDefinition]] = {
    collection.find().toFuture()
  }

  def fetchAllByTopLevelContext(topLevelContext: String): Future[Seq[APIDefinition]] = {
    collection.find(regex("context", f"^$topLevelContext\\/.*$$")).toFuture()
  }

  def delete(serviceName: String): Future[Unit] = {
    collection.deleteOne(serviceNameSelector(serviceName))
      .toFuture()
      .map(_ => logger.info(s"API with service name '$serviceName' has been deleted successfully"))
  }
}
