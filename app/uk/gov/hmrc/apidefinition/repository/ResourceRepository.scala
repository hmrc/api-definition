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
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONBinary, BSONDocument, BSONObjectID, Subtype}
import reactivemongo.play.json.ImplicitBSONHandlers.{BSONDocumentFormat, BSONDocumentWrites}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceRepository @Inject()(mongo: ReactiveMongoComponent)(implicit val ec: ExecutionContext)

  extends ReactiveRepository[ResourceData, BSONObjectID](
    collectionName = "resource",
    mongo = mongo.mongoConnector.db,
    domainFormat = ResourceDataMongoFormat.formatApplicationData,
    idFormat = ReactiveMongoFormats.objectIdFormats) {

  def deleteResourcesForService(serviceName: String): Future[Unit] = {

    val query = BSONDocument("serviceName" -> serviceName)

    collection
      .delete(ordered = false)
      .one(q = query, limit = None, collation = None)
      .map { _ => Unit }
  }

  override def indexes: Seq[Index] = {

    val fields = Seq("serviceName", "version", "resource")

    val index = Index(
      key = fields.map(_ -> Ascending),
      name = Some("serviceName_version_resource_Index"),
      unique = true,
      background = true)

    Seq(index)
  }

  def save(resourceData: ResourceData): Future[Unit] = {

    Logger.info(s"Saving in mongo, documentation resource: ${resourceData.serviceName}, ${resourceData.version}, ${resourceData.resource}")

    val query = createQuery(resourceData.serviceName, resourceData.version, resourceData.resource)

    val documentToSave = BSONDocument(
      "serviceName" -> resourceData.serviceName,
      "version" -> resourceData.version,
      "resource" -> resourceData.resource,
      "contents" -> BSONBinary(resourceData.contents, Subtype.GenericBinarySubtype)
    )

    collection
      .update(ordered = false)
      .one(query, documentToSave, upsert = true)
      .map(_ => Unit)
  }

  private def createQuery(serviceName: String, version: String, resource: String): BSONDocument = {
    BSONDocument(
      "serviceName" -> serviceName,
      "version" -> version,
      "resource" -> resource
    )
  }

  def fetch(serviceName: String, version: String, resource: String): Future[Option[ResourceData]] = {

    Logger.info(s"Querying mongo to find documentation resource: $serviceName, $version, $resource")

    val query = createQuery(serviceName, version, resource)

    collection
      .find(query, None)
      .one[BSONDocument]
      .map(_.map(ResourceData.apply))
  }

  def findAll()(implicit ec: scala.concurrent.ExecutionContext): Future[scala.List[ResourceData]] = {
    collection
      .find(BSONDocument.empty, None)
      .cursor[BSONDocument]()
      .collect[List](maxDocs = -1, err = Cursor.FailOnError[List[BSONDocument]]())
      .map(_.map(ResourceData.apply))
  }
}

case class ResourceData(serviceName: String, version: String, resource: String, contents: Array[Byte])

object ResourceData {
  def apply(doc: BSONDocument): ResourceData = {
    val serviceName = doc.getAs[String]("serviceName").get
    val version = doc.getAs[String]("version").get
    val resource = doc.getAs[String]("resource").get
    val contents = doc.getAs[BSONBinary]("contents").get.byteArray

    ResourceData(serviceName, version, resource, contents)
  }
}

object ResourceDataMongoFormat {
  implicit val formatApplicationData: OFormat[ResourceData] = Json.format[ResourceData]
}
