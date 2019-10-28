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

package it

import akka.util.ByteString
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONBinary, BSONDocument, BSONValue}
import reactivemongo.play.json.ImplicitBSONHandlers.{BSONDocumentFormat, BSONDocumentWrites}
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.apidefinition.repository.{ResourceData, ResourceRepository}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global

class ResourceRepositorySpec extends UnitSpec with MongoSpecSupport
  with BeforeAndAfterEach with BeforeAndAfterAll with IndexVerification
  with MockitoSugar with Eventually {

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val resourceRepository = new ResourceRepository(reactiveMongoComponent)

  override def beforeEach() {
    await(resourceRepository.drop)
    await(resourceRepository.ensureIndexes)
  }

  def aResourceData: ResourceData = {
    ResourceData("example-service", "1.0", "application.raml", ByteString("Some content").toArray[Byte])
  }

  "The 'resource' collection" should {

    "have all the current indexes" in {

      val expectedIndexes = Set(
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"), unique = false, background = false),
        Index(key = Seq("serviceName" -> Ascending, "version" -> Ascending, "resource" -> Ascending),
          name = Some("serviceName_version_resource_Index"),
          background = true,
          unique = true)
      )

      verifyIndexesVersionAgnostic(resourceRepository, expectedIndexes)
    }
  }

  "save" should {

    "create a new resource when one doesn't exist" in {

      val resourceData = aResourceData

      await(resourceRepository.save(resourceData))

      val maybeRetrieved = await(resourceRepository.fetch(resourceData.serviceName, resourceData.version, resourceData.resource))

      assertResourceDataSame(maybeRetrieved.get, resourceData)

      val count = await(resourceRepository.count)
      count shouldBe 1
    }

    "set the MongoDB document 'contents' field type as BSONBinary" in {
      val resourceData = aResourceData

      await(resourceRepository.save(resourceData))

      val collection = reactiveMongoComponent
        .mongoConnector
        .db()
        .collection[JSONCollection](resourceRepository.collection.name)

      val document: BSONDocument = await(collection
        .find(BSONDocument.empty, None)
        .one[BSONDocument])
        .get

      val contentsValue: BSONValue = document.get("contents").get

      contentsValue shouldBe a[BSONBinary]
    }

    "update an existing resource when it already exists" in {

      val resourceData = aResourceData

      await(resourceRepository.save(resourceData))

      val maybeRetrieved = await(resourceRepository.fetch(resourceData.serviceName, resourceData.version, resourceData.resource))

      assertResourceDataSame(maybeRetrieved.get, resourceData)
      val retrieved = maybeRetrieved.get

      val updated = retrieved.copy(contents = ByteString("new contents").toArray[Byte])

      await(resourceRepository.save(updated))

      val newMaybeRetrieved = await(resourceRepository.fetch(resourceData.serviceName, resourceData.version, resourceData.resource))

      assertResourceDataSame(newMaybeRetrieved.get, updated)
      val count = await(resourceRepository.count)
      count shouldBe 1
    }

  }

  "fetch" should {

    "return None when the requested resource doesn't exist" in {

      val resourceData = aResourceData
      val maybeRetrieved = await(resourceRepository.fetch(resourceData.serviceName, resourceData.version, resourceData.resource))

      maybeRetrieved shouldBe None

    }

    "return the resourceData that matches service name, version and resource" in {
      val expectedResourceData = aResourceData
      val otherResourceData1 = expectedResourceData.copy(serviceName = "different-service-name")
      val otherResourceData2 = expectedResourceData.copy(version = "different.version")
      val otherResourceData3 = expectedResourceData.copy(resource = "differentResource.raml")
      await(resourceRepository.save(expectedResourceData))
      await(resourceRepository.save(otherResourceData1))
      await(resourceRepository.save(otherResourceData2))
      await(resourceRepository.save(otherResourceData3))

      val newMaybeRetrieved = await(resourceRepository.fetch(expectedResourceData.serviceName, expectedResourceData.version, expectedResourceData.resource))

      assertResourceDataSame(newMaybeRetrieved.get, expectedResourceData)
    }
  }

  "Delete resources for service" should {
    "delete all the resources in MongoDB for the service" in {
      val sameServiceData1 = aResourceData
      val sameServiceData2WithDifferentVersion = sameServiceData1.copy(version = "different.version")
      val sameOtherResourceData3WithDifferentResource = sameServiceData1.copy(resource = "differentResource.raml")

      val otherServiceData = aResourceData.copy(serviceName = "different-service-name")


      await(resourceRepository.save(sameServiceData1))
      await(resourceRepository.save(sameServiceData2WithDifferentVersion))
      await(resourceRepository.save(sameOtherResourceData3WithDifferentResource))
      await(resourceRepository.save(otherServiceData))

      await(resourceRepository.deleteResourcesForService(sameServiceData1.serviceName))

      val allResources: immutable.Seq[ResourceData] = await(resourceRepository.findAll())

      allResources.length shouldBe 1
      assertResourceDataSame(allResources.head, otherServiceData)
    }
  }

  override protected def afterAll() {
    resourceRepository.drop
  }

  def assertResourceDataSame(expectedResourceData: ResourceData, actualResourceData: ResourceData) : Unit = {
    actualResourceData.contents shouldBe expectedResourceData.contents
    actualResourceData.serviceName shouldBe expectedResourceData.serviceName
    actualResourceData.resource shouldBe expectedResourceData.resource
    actualResourceData.version shouldBe expectedResourceData.version
  }
}
