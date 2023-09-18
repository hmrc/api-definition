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

import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{equal, regex}
import org.mongodb.scala.model.{FindOneAndReplaceOptions, ReturnDocument}

import play.api.Logging
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}

import uk.gov.hmrc.apidefinition.utils.IndexHelper.createUniqueBackgroundSingleFieldAscendingIndex
import uk.gov.hmrc.apidefinition.models.TolerantJsonApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

@Singleton
class APIDefinitionRepository @Inject() (mongoComponent: MongoComponent)(implicit val ec: ExecutionContext)
    extends PlayMongoRepository[ApiDefinition](
      collectionName = "api",
      mongoComponent = mongoComponent,
      domainFormat = TolerantJsonApiDefinition.tolerantFormatApiDefinition,
      indexes = Seq("context", "name", "serviceName", "serviceBaseUrl")
        .map(fieldName => createUniqueBackgroundSingleFieldAscendingIndex(fieldName, s"${fieldName}Index"))
    ) with Logging {

  override lazy val collection: MongoCollection[ApiDefinition] =
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

  private def serviceNameSelector(serviceName: String): Bson = {
    equal("serviceName", Codecs.toBson(serviceName))
  }

  def save(apiDefinition: ApiDefinition): Future[ApiDefinition] = {
    collection.findOneAndReplace(
      serviceNameSelector(apiDefinition.serviceName),
      apiDefinition,
      FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
    ).head()
  }

  def fetchByServiceName(serviceName: String): Future[Option[ApiDefinition]] = {
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

  def fetchByServiceBaseUrl(serviceBaseUrl: String): Future[Option[ApiDefinition]] = {
    collection.find(equal("serviceBaseUrl", Codecs.toBson(serviceBaseUrl))).headOption().map { api =>
      logger.debug(s"Retrieved API with service base url '$serviceBaseUrl' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with service base url '$serviceBaseUrl' in mongo", e)
        throw e
    }
  }

  def fetchByName(name: String): Future[Option[ApiDefinition]] = {
    collection.find(equal("name", Codecs.toBson(name))).headOption().map { api =>
      logger.debug(s"Retrieved API with name '$name' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with name '$name' in mongo", e)
        throw e
    }
  }

  def fetchByContext(context: ApiContext): Future[Option[ApiDefinition]] = {
    collection.find(equal("context", Codecs.toBson(context.value))).headOption().map { api =>
      logger.debug(s"Retrieved API with context '${context.value}' in mongo: $api")
      api
    } recover {
      case e =>
        logger.error(s"An error occurred while retrieving API with context '${context.value}' in mongo", e)
        throw e
    }
  }

  def fetchAll(): Future[Seq[ApiDefinition]] = {
    collection.find().toFuture()
  }

  def fetchAllByTopLevelContext(topLevelContext: ApiContext): Future[Seq[ApiDefinition]] = {
    collection.find(regex("context", f"^${topLevelContext.value}\\/.*$$")).toFuture()
  }

  def delete(serviceName: String): Future[Unit] = {
    collection.deleteOne(serviceNameSelector(serviceName))
      .toFuture()
      .map(_ => logger.info(s"API with service name '$serviceName' has been deleted successfully"))
  }
}
