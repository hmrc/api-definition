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

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.mongodb.MongoWriteException
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.*
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition.ServiceBaseUrl
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.Endpoint.*
import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class APIDefinitionRepositorySpec extends AsyncHmrcSpec
    with DefaultPlayMongoRepositorySupport[StoredApiDefinition]
    with GuiceOneAppPerSuite with BeforeAndAfterEach
    with BeforeAndAfterAll with Eventually {

  private def withSource(source: ApiVersionSource)(apiVersion: ApiVersion): ApiVersion = {
    apiVersion.copy(versionSource = source)
  }

  private def defnWithSource(source: ApiVersionSource)(apiDefn: StoredApiDefinition): StoredApiDefinition = {
    apiDefn.copy(versions = apiDefn.versions.map(withSource(source)(_)))
  }
  override implicit lazy val app: Application                                                             = appBuilder.build()

  private val helloApiVersion = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.Beta,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/world"), Name("Say Hello to the World!"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.Unknown
  )

  private val calendarApiVersion = ApiVersion(
    versionNbr = ApiVersionNbr("2.0"),
    status = ApiStatus.Stable,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/date"), Name("Check current date"), HttpMethod.Get, AuthType.None, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val helloApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("hello-service"),
    serviceBaseUrl = ServiceBaseUrl("hello.com"),
    name = ApiDefinition.Name("Hello"),
    description = ApiDefinition.Description("This is the Hello API"),
    context = ApiContext("hello"),
    versions = List(helloApiVersion),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  private val calendarApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("calendar-service"),
    serviceBaseUrl = ServiceBaseUrl("calendar.com"),
    name = ApiDefinition.Name("Calendar"),
    description = ApiDefinition.Description("This is the Calendar API"),
    context = ApiContext("calendar"),
    versions = List(calendarApiVersion),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  private val individualIncomeTaxApiVersion = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.Stable,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/submit"), Name("Submit Income Tax Return"), HttpMethod.Post, AuthType.User, ResourceThrottlingTier.Unlimited, None, List.empty)),
    None,
    ApiVersionSource.OAS
  )

  private val individualIncomeTaxApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("income-tax"),
    serviceBaseUrl = ServiceBaseUrl("income-tax.protected.mdtp"),
    name = ApiDefinition.Name("Individual Income Tax"),
    description = ApiDefinition.Description("This is the Individual Income Tax API"),
    context = ApiContext("individuals/income-tax"),
    versions = List(individualIncomeTaxApiVersion),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  private val individualNIApiVersion = ApiVersion(
    versionNbr = ApiVersionNbr("1.0"),
    status = ApiStatus.Stable,
    access = ApiAccessType.Public,
    endpoints = List(Endpoint(UriPattern("/submit"), Name("Submit National Insurance"), HttpMethod.Post, AuthType.User, ResourceThrottlingTier.Unlimited, None, List.empty)),
    awsRequestId = None,
    versionSource = ApiVersionSource.Unknown
  )

  private val individualNIApiDefinition = StoredApiDefinition(
    serviceName = ServiceName("ni"),
    serviceBaseUrl = ServiceBaseUrl("ni.protected.mdtp"),
    name = ApiDefinition.Name("Individual National Insurance"),
    description = ApiDefinition.Description("This is the Individual National Insurance API"),
    context = ApiContext("individuals/ni"),
    versions = List(individualNIApiVersion),
    isTestSupport = false,
    lastPublishedAt = None,
    categories = List(ApiCategory.Agents)
  )

  override val repository: APIDefinitionRepository = app.injector.instanceOf[APIDefinitionRepository]

  private def saveApi(repo: APIDefinitionRepository, apiDefinition: StoredApiDefinition): Future[StoredApiDefinition] = {
    repo.collection.insertOne(apiDefinition).toFuture().map(_ => apiDefinition)
  }

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  private def collectionSize: Long = {
    await(repository.collection.countDocuments().head())
  }

  "save()" should {

    "create a new API definition in Mongo" in {

      val aTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

      val apiDefinition = calendarApiDefinition.copy(lastPublishedAt = Some(aTime))
      await(repository.save(apiDefinition))

      val retrieved = await(repository.fetchByServiceName(apiDefinition.serviceName))
      retrieved shouldBe Some(apiDefinition)
    }

    "update an existing API definition in Mongo" in {

      await(repository.save(helloApiDefinition))

      val updatedAPIDefinition = helloApiDefinition.copy(name = ApiDefinition.Name("Ciao"), description = ApiDefinition.Description("Ciao API"), versions = List(calendarApiVersion))
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
      retrieved shouldBe List(helloApiDefinition, calendarApiDefinition)

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
      await(repository.save(calendarApiDefinition.copy(serviceName = ServiceName("abc"))))

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
      await(repository.save(calendarApiDefinition.copy(serviceBaseUrl = ServiceBaseUrl("abc"))))

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
      await(repository.save(calendarApiDefinition.copy(name = ApiDefinition.Name("abc"))))

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
      await(repository.save(calendarApiDefinition.copy(context = ApiContext("abc"))))

      val retrieved = await(repository.fetchByContext(calendarApiDefinition.context))
      retrieved shouldBe None
    }
  }

  "fetchAllByTopLevelContext()" should {
    "fetch API definitions starting with the given top level context" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(individualIncomeTaxApiDefinition))
      await(repository.save(individualNIApiDefinition))

      val retrieved = await(repository.fetchAllByTopLevelContext(ApiContext("individuals")))

      retrieved.size shouldBe 2
    }

    "return an empty collection when there are no matching API Definitions" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      val retrieved = await(repository.fetchAllByTopLevelContext(ApiContext("individuals")))

      retrieved.size shouldBe 0
    }
  }

  "delete()" should {

    "delete the API definitions in Mongo" in {
      await(repository.save(helloApiDefinition))
      await(repository.save(calendarApiDefinition))

      await(repository.delete(calendarApiDefinition.serviceName))

      val retrieved = await(repository.fetchAll())
      retrieved shouldBe List(defnWithSource(ApiVersionSource.Unknown)(helloApiDefinition))
    }

  }

  "The 'api' Mongo collection" should {

    def assertMongoError(caught: MongoWriteException, fieldName: String, duplicateFieldValue: String): Unit = {

      caught.getCode shouldBe 11000

      // Mongo 3.x and 4.x return slightly different error messages for dup keys.
      val mongpV3ErrorMessage = s"""E11000 duplicate key error collection: test-APIDefinitionRepositorySpec.api index: ${fieldName}Index dup key: { : "$duplicateFieldValue" }"""
      val mongoV4ErrorMessage =
        s"""E11000 duplicate key error collection: test-APIDefinitionRepositorySpec.api index: ${fieldName}Index dup key: { $fieldName: "$duplicateFieldValue" }"""

      val errors = List(mongpV3ErrorMessage, mongoV4ErrorMessage)

      errors.contains(caught.getError.getMessage) shouldBe true
    }

    "have a unique index based on `context`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[MongoWriteException] {
        val inError = saveApi(
          repository,
          helloApiDefinition.copy(serviceName = ServiceName("newServiceName"), name = ApiDefinition.Name("newName"), serviceBaseUrl = ServiceBaseUrl("newServiceBaseUrl"))
        )
        await(inError)
      }
      assertMongoError(caught, "context", helloApiDefinition.context.value)

      collectionSize shouldBe 1
    }

    "have a unique index based on `name`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[MongoWriteException] {
        val inError =
          saveApi(
            repository,
            helloApiDefinition.copy(context = ApiContext("newContext"), serviceName = ServiceName("newServiceName"), serviceBaseUrl = ServiceBaseUrl("newServiceBaseUrl"))
          )
        await(inError)
      }
      assertMongoError(caught, "name", helloApiDefinition.name)

      collectionSize shouldBe 1
    }

    "have a unique index based on `serviceName`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[MongoWriteException] {
        val inError = saveApi(
          repository,
          helloApiDefinition.copy(name = ApiDefinition.Name("newName"), context = ApiContext("newContext"), serviceBaseUrl = ServiceBaseUrl("newServiceBaseUrl"))
        )
        await(inError)
      }
      assertMongoError(caught, "serviceName", helloApiDefinition.serviceName)

      collectionSize shouldBe 1
    }

    "have a unique index based on `serviceBaseUrl`" in {
      await(repository.save(helloApiDefinition))
      collectionSize shouldBe 1

      val caught = intercept[MongoWriteException] {
        val inError =
          saveApi(repository, helloApiDefinition.copy(name = ApiDefinition.Name("newName"), context = ApiContext("newContext"), serviceName = ServiceName("newServiceName")))
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
      val expectedIndexes = List(
        IndexModel(ascending("context"), IndexOptions().name("contextIndex").background(true).unique(true)),
        IndexModel(ascending("name"), IndexOptions().name("nameIndex").background(true).unique(true)),
        IndexModel(ascending("serviceName"), IndexOptions().name("serviceNameIndex").background(true).unique(true)),
        IndexModel(ascending("serviceBaseUrl"), IndexOptions().name("serviceBaseUrlIndex").background(true).unique(true))
      )

      indexes.toSet.toString shouldBe expectedIndexes.toSet.toString
    }

    "use the tolerant readers" in {
      val rawJson = Json.obj(
        "serviceName"    -> "calendar",
        "name"           -> "Calendar API",
        "description"    -> "My Calendar API",
        "serviceBaseUrl" -> "http://calendar",
        "context"        -> "individuals/calendar",
        "requiresTrust"  -> true,
        "categories"     -> Seq("OTHER"),
        "versions"       -> Seq(
          Json.obj(
            "version"   -> "1.0",
            "status"    -> "STABLE",
            "endpoints" -> Seq(
              Json.obj(
                "uriPattern"     -> UriPattern("/today"),
                "endpointName"   -> Name("Get Today's Date"),
                "method"         -> "GET",
                "authType"       -> "NONE",
                "throttlingTier" -> "UNLIMITED"
              )
            )
          )
        )
      )

      await(mongoDatabase.getCollection("api").insertOne(Document(rawJson.toString())).toFuture())

      val records = await(repository.fetchAll())

      records.size shouldBe 1

      records.head.isTestSupport shouldBe false
      records.head.lastPublishedAt shouldBe None

      records.head.versions.head.access shouldBe ApiAccessType.Public
      records.head.versions.head.awsRequestId shouldBe None
      records.head.versions.head.versionSource shouldBe ApiVersionSource.Unknown

      records.head.versions.head.endpoints.head.queryParameters shouldBe List.empty

    }
  }

}
