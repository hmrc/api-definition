/*
 * Copyright 2018 HM Revenue & Customs
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

import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class APIDefinitionRepositorySpec extends UnitSpec
  with MongoSpecSupport with BeforeAndAfterEach
  with BeforeAndAfterAll with Eventually {

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val repository = createRepository()

  private def saveApi(repo: APIDefinitionRepository, apiDefinition: APIDefinition): Future[APIDefinition] = {
    repo.collection.insert(apiDefinition).map(_ => apiDefinition)
  }

  private def getIndexes(repo: APIDefinitionRepository): List[Index] = {
    val indexesFuture = repo.collection.indexesManager.list()
    await(indexesFuture)
  }

  private def createRepository() = {
    new APIDefinitionRepository(reactiveMongoComponent)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  override def beforeEach() {
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override protected def afterAll() {
    await(repository.drop)
  }

  "createOrUpdate" should {

    "create a new API Definition in Mongo and fetch that same API Definition" in {
      val aTime = DateTime.now(DateTimeZone.UTC)

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), None, None, Some(aTime))
      await(repository.save(apiDefinition))

      val retrieved = await(repository.fetchByServiceName(apiDefinition.serviceName))

      retrieved shouldBe Some(apiDefinition)

    }

    "update an existing API Definition in Mongo and fetch that same API Definition" in {

      val apiDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), None)
      await(repository.save(apiDefinition))

      val updatedAPIDefinition = APIDefinition("calendar", "http://calendar", "Calendar API", "My Updated Calendar API", "calendar", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), None)
      await(repository.save(updatedAPIDefinition))

      val retrieved = await(repository.fetchByServiceName(apiDefinition.serviceName)).get

      retrieved shouldBe updatedAPIDefinition

    }

  }

  "fetchAll" should {

    "return all API Definitions in Mongo" in {

      val apiDefinition1 = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), None)
      val apiDefinition2 = APIDefinition("employment", "http://employment", "Employment API", "My Calendar API", "employment", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/history", "Get Employment History", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(true))

      await(repository.save(apiDefinition1))
      await(repository.save(apiDefinition2))

      val retrieved = await(repository.fetchAll())

      retrieved shouldBe Seq(apiDefinition1, apiDefinition2)

    }

  }

//  TODO: add scenario for "fetchByName"

//  TODO: add scenario for "fetchByServiceName"

  "fetchByContext" should {

    "return the API Definition" in {

      val apiDefinition1 = APIDefinition("calendar-api", "http://calendar", "Calendar API", "My Calendar API", "calendar", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), None)
      val apiDefinition2 = APIDefinition("employment-api", "http://employment", "Employment API", "My Calendar API", "employment", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/history", "Get Employment History", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(true))

      await(repository.save(apiDefinition1))
      await(repository.save(apiDefinition2))

      val retrieved = await(repository.fetchByContext("calendar"))

      retrieved shouldBe Some(apiDefinition1)
    }

    "return None when no API exists for the context" in {

      val retrieved = await(repository.fetchByContext("calendar"))

      retrieved shouldBe None
    }
  }

  "delete" should {

    "delete the API Definitions in Mongo" in {

      val apiDefinition1 = APIDefinition("calendar", "http://calendar", "Calendar API", "My Calendar API", "calendar", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), None)
      val apiDefinition2 = APIDefinition("employment", "http://employment", "Employment API", "My Calendar API", "employment", Seq(APIVersion("1.0", APIStatus.PROTOTYPED, Some(PublicAPIAccess()), Seq(Endpoint("/history", "Get Employment History", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))), Some(true))
      await(repository.save(apiDefinition1))
      await(repository.save(apiDefinition2))

      await(repository.delete("calendar"))

      val retrieved = await(repository.fetchAll())
      retrieved shouldBe Seq(apiDefinition2)
    }

  }

  "The 'api' collection" should {

    val apiVersion = APIVersion(
      version = "1.0",
      status = APIStatus.PROTOTYPED,
      access = None,
      endpoints = Seq(Endpoint("/hello", "Hello", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
    )

    val apiDefinition = APIDefinition(
      serviceName = "serviceName",
      serviceBaseUrl = "serviceBaseUrl",
      name = "name",
      description = "description",
      context = "context",
      versions = Seq(apiVersion),
      requiresTrust = None)

    "have a unique index based on `context`" in {
      await(repository.save(apiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, apiDefinition.copy(serviceName = "newServiceName", name = "newName"))
        await(inError)
      }
      caught.code shouldBe Some(11000)
      caught.message shouldBe "E11000 duplicate key error collection: test-APIDefinitionRepositorySpec.api index: contextIndex dup key: { : \"context\" }"

      collectionSize shouldBe 1
    }

    "have a unique index based on `name`" in {
      await(repository.save(apiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, apiDefinition.copy(context = "newContext", serviceName = "newServiceName"))
        await(inError)
      }
      caught.code shouldBe Some(11000)
      caught.message shouldBe "E11000 duplicate key error collection: test-APIDefinitionRepositorySpec.api index: nameIndex dup key: { : \"name\" }"

      collectionSize shouldBe 1
    }

    "have a unique index based on `serviceName`" in {
      await(repository.save(apiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, apiDefinition.copy(name = "newName", context = "newContext"))
        await(inError)
      }
      caught.code shouldBe Some(11000)
      caught.message shouldBe "E11000 duplicate key error collection: test-APIDefinitionRepositorySpec.api index: serviceNameIndex dup key: { : \"serviceName\" }"

      collectionSize shouldBe 1
    }

    "insert a new record when `context`, `name` and `serviceName` are unique" in {
      await(repository.save(apiDefinition))
      collectionSize shouldBe 1

      await(saveApi(repository, apiDefinition.copy(name = "newName", context = "newContext", serviceName = "newServiceName")))
      collectionSize shouldBe 2
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val indexVersion = Some(1)
      val expectedIndexes = List(
        Index(key = Seq("context" -> Ascending), name = Some("contextIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("serviceName" -> Ascending), name = Some("serviceNameIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("name" -> Ascending), name = Some("nameIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"), version = indexVersion)
      )

      val repo = createRepository()

      eventually(timeout(4.seconds), interval(100.milliseconds)) {
        getIndexes(repo).toSet shouldBe expectedIndexes.toSet
      }

      await(repo.drop) shouldBe true
    }
  }

}
