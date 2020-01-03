/*
 * Copyright 2020 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.repository

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadConcern
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class APIDefinitionRepositorySpec extends UnitSpec
  with MongoSpecSupport with BeforeAndAfterEach
  with BeforeAndAfterAll with Eventually {

  private val helloApiVersion = APIVersion(
    version = "1.0",
    status = APIStatus.PROTOTYPED,
    access = None,
    endpoints = Seq(Endpoint("/world", "Say Hello to the World!", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val calendarApiVersion = APIVersion(
    version = "2.0",
    status = APIStatus.PUBLISHED,
    access = None,
    endpoints = Seq(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val helloApiDefinition = APIDefinition(
    serviceName = "hello-service",
    serviceBaseUrl = "hello.com",
    name = "Hello",
    description = "This is the Hello API",
    context = "hello",
    versions = Seq(helloApiVersion),
    requiresTrust = None)

  private val calendarApiDefinition = APIDefinition(
    serviceName = "calendar-service",
    serviceBaseUrl = "calendar.com",
    name = "Calendar",
    description = "This is the Calendar API",
    context = "calendar",
    versions = Seq(calendarApiVersion),
    requiresTrust = None)

  private val individualIncomeTaxApiVersion = APIVersion(
    version = "1.0",
    status = APIStatus.PUBLISHED,
    access = None,
    endpoints = Seq(Endpoint("/submit", "Submit Income Tax Return", HttpMethod.POST, AuthType.USER, ResourceThrottlingTier.UNLIMITED))
  )

  private val individualIncomeTaxApiDefinition = APIDefinition(
    serviceName = "income-tax",
    serviceBaseUrl = "income-tax.protected.mdtp",
    name = "Individual Income Tax",
    description = "This is the Individual Income Tax API",
    context = "individuals/income-tax",
    versions = Seq(individualIncomeTaxApiVersion),
    requiresTrust = None)

  private val individualNIApiVersion = APIVersion(
    version = "1.0",
    status = APIStatus.PUBLISHED,
    access = None,
    endpoints = Seq(Endpoint("/submit", "Submit National Insurance", HttpMethod.POST, AuthType.USER, ResourceThrottlingTier.UNLIMITED))
  )

  private val individualNIApiDefinition = APIDefinition(
    serviceName = "ni",
    serviceBaseUrl = "ni.protected.mdtp",
    name = "Individual National Insurance",
    description = "This is the Individual National Insurance API",
    context = "individuals/ni",
    versions = Seq(individualNIApiVersion),
    requiresTrust = None)

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val repository = createRepository()

  private def saveApi(repo: APIDefinitionRepository, apiDefinition: APIDefinition): Future[APIDefinition] = {
    repo.collection.insert(ordered = false).one(apiDefinition).map(_ => apiDefinition)
  }

  private def getIndexes(repo: APIDefinitionRepository): List[Index] = {
    val indexesFuture = repo.collection.indexesManager.list()
    await(indexesFuture)
  }

  private def createRepository(): APIDefinitionRepository = {
    new APIDefinitionRepository(reactiveMongoComponent)
  }

  private def collectionSize: Long = {
    await(repository.collection.count(None,None,0,None,ReadConcern.Majority))
  }

  override def beforeEach(): Unit = {
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override protected def afterAll(): Unit = {
    await(repository.drop)
  }

  "save()" should {

    "create a new API definition in Mongo" in {

      val aTime = DateTime.now(DateTimeZone.UTC)

      val apiDefinition = calendarApiDefinition.copy(lastPublishedAt = Some(aTime))
      await(repository.save(apiDefinition))

      val retrieved = await(repository.fetchByServiceName(apiDefinition.serviceName))
      retrieved shouldBe Some(apiDefinition)

    }

    "update an existing API definition in Mongo" in {

      await(repository.save(helloApiDefinition))

      val updatedAPIDefinition = helloApiDefinition.copy(name = "Ciao", description = "Ciao API", versions = Seq(calendarApiVersion))
      await(repository.save(updatedAPIDefinition))

      val retrieved = await(repository.fetchByServiceName(helloApiDefinition.serviceName)).get
      retrieved shouldBe updatedAPIDefinition

    }

  }

  "fetchAll()" should {

    "return all API definitions in Mongo" in {

      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchAll())
      retrieved shouldBe Seq(helloApiDefinition, calendarApiDefinition)

    }

  }

  "fetchByServiceName()" should {

    "return the expected API definition" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchByServiceName(calendarApiDefinition.serviceName))
      retrieved shouldBe Some(calendarApiDefinition)
    }

    "return None when there are no APIs with that service name" in {
      await(repository.save(calendarApiDefinition.copy(serviceName = "abc")))

      val retrieved = await(repository.fetchByServiceName(calendarApiDefinition.serviceName))
      retrieved shouldBe None
    }
  }

  "fetchByServiceBaseUrl()" should {

    "return the expected API definition" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchByServiceBaseUrl(calendarApiDefinition.serviceBaseUrl))
      retrieved shouldBe Some(calendarApiDefinition)
    }

    "return None when there are no APIs with that service name" in {
      await(repository.save(calendarApiDefinition.copy(serviceBaseUrl = "abc")))

      val retrieved = await(repository.fetchByServiceBaseUrl(calendarApiDefinition.serviceBaseUrl))
      retrieved shouldBe None
    }
  }

  "fetchByName()" should {

    "return the expected API definition" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchByName(calendarApiDefinition.name))

      retrieved shouldBe Some(calendarApiDefinition)
    }

    "return None when there are no APIs with that name" in {
      await(repository.save(calendarApiDefinition.copy(name = "abc")))

      val retrieved = await(repository.fetchByName(calendarApiDefinition.name))
      retrieved shouldBe None
    }
  }

  "fetchByContext()" should {

    "return the expected API definition" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchByContext(calendarApiDefinition.context))
      retrieved shouldBe Some(calendarApiDefinition)
    }

    "return None when there are no APIs with that context" in {
      await(repository.save(calendarApiDefinition.copy(context = "abc")))

      val retrieved = await(repository.fetchByContext(calendarApiDefinition.context))
      retrieved shouldBe None
    }
  }

  "fetchAllByTopLevelContext()" should {
    "fetch API definitions starting with the given top level context" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(individualIncomeTaxApiDefinition))
      await(repository.save(individualNIApiDefinition))

      val retrieved = await(repository.fetchAllByTopLevelContext("individuals"))

      retrieved.size shouldBe 2
    }

    "return an empty collection when there are no matching API Definitions" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchAllByTopLevelContext("individuals"))

      retrieved.size shouldBe 0
    }
  }

  "delete()" should {

    "delete the API definitions in Mongo" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      await(repository.delete(calendarApiDefinition.serviceName))

      val retrieved = await(repository.fetchAll())
      retrieved shouldBe Seq(helloApiDefinition)
    }

  }

  "The 'api' Mongo collection" should {

    def assertMongoError(caught: DatabaseException, fieldName: String, duplicateFieldValue: String): Unit = {
      caught.code shouldBe Some(11000)
      caught.message shouldBe s"""E11000 duplicate key error collection: test-APIDefinitionRepositorySpec.api index: ${fieldName}Index dup key: { : "$duplicateFieldValue" }"""
    }

    "have a unique index based on `context`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, helloApiDefinition.copy(serviceName = "newServiceName", name = "newName", serviceBaseUrl = "newServiceBaseUrl"))
        await(inError)
      }
      assertMongoError(caught, "context", helloApiDefinition.context)

      collectionSize shouldBe 1
    }

    "have a unique index based on `name`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, helloApiDefinition.copy(context = "newContext", serviceName = "newServiceName", serviceBaseUrl = "newServiceBaseUrl"))
        await(inError)
      }
      assertMongoError(caught, "name", helloApiDefinition.name)

      collectionSize shouldBe 1
    }

    "have a unique index based on `serviceName`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, helloApiDefinition.copy(name = "newName", context = "newContext", serviceBaseUrl = "newServiceBaseUrl"))
        await(inError)
      }
      assertMongoError(caught, "serviceName", helloApiDefinition.serviceName)

      collectionSize shouldBe 1
    }

    "have a unique index based on `serviceBaseUrl`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val inError = saveApi(repository, helloApiDefinition.copy(name = "newName", context = "newContext", serviceName = "newServiceName"))
        await(inError)
      }
      assertMongoError(caught, "serviceBaseUrl", helloApiDefinition.serviceBaseUrl)

      collectionSize shouldBe 1
    }

    "insert a new record when `context`, `name`, `serviceName` and `serviceBaseUrl` are unique" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      await(saveApi(repository, calendarApiDefinition))
      collectionSize shouldBe 2
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val indexVersion = Some(2)
      val expectedIndexes = List(
        Index(key = Seq("context" -> Ascending), name = Some("contextIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("serviceName" -> Ascending), name = Some("serviceNameIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("serviceBaseUrl" -> Ascending), name = Some("serviceBaseUrlIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("name" -> Ascending), name = Some("nameIndex"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"), version = indexVersion)
      )

      val repo = createRepository()

      eventually(timeout(3.seconds), interval(100.milliseconds)) {
        getIndexes(repo).toSet shouldBe expectedIndexes.toSet
      }

      await(repo.drop) shouldBe true
    }
  }

}
