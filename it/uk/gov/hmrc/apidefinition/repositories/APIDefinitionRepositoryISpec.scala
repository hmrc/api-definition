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

package uk.gov.hmrc.apidefinition.repositories

import java.util.UUID
import org.mongodb.scala.ReadPreference.primaryPreferred
import org.mongodb.scala.bson.{BsonBoolean, BsonDocument}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccess, ApiCategory, ApiStatus, ApiVersion, AuthType, Endpoint, HttpMethod, ResourceThrottlingTier, ServiceName, StoredApiDefinition}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}

class APIDefinitionRepositoryISpec extends AnyWordSpec with PlayMongoRepositorySupport[StoredApiDefinition] with Matchers with BeforeAndAfterEach with GuiceOneAppPerSuite with IntegrationPatience {
  val serviceRepo = repository.asInstanceOf[APIDefinitionRepository]

  override implicit lazy val app: Application = appBuilder.build()

  override def beforeEach(): Unit = {
    prepareDatabase()
  }

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  override protected def repository: PlayMongoRepository[StoredApiDefinition] = app.injector.instanceOf[APIDefinitionRepository]

  trait Setup {

    val testApiVersion1 = ApiVersion(
      versionNbr = ApiVersionNbr("1.0"),
      status = ApiStatus.STABLE,
      access = ApiAccess.PUBLIC,
      endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
    )

    val testApiVersion2 = ApiVersion(
      versionNbr = ApiVersionNbr("2.0"),
      status = ApiStatus.STABLE,
      access = ApiAccess.PUBLIC,
      endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
    )

    val testApiVersion3 = ApiVersion(
      versionNbr = ApiVersionNbr("3.0"),
      status = ApiStatus.STABLE,
      access = ApiAccess.PUBLIC,
      endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
    )

    val retiredApiVersion3 = ApiVersion(
      versionNbr = ApiVersionNbr("3.0"),
      status = ApiStatus.RETIRED,
      access = ApiAccess.PUBLIC,
      endpoints = List(Endpoint("/date", "Check current date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
    )

    val definition = StoredApiDefinition (
      serviceName = ServiceName("api1"),
      serviceBaseUrl = "test.com",
      name = "Test",
      description = "This is the Test API",
      context = ApiContext("test"),
      versions = List(testApiVersion1),
      requiresTrust = false,
      isTestSupport = false,
      lastPublishedAt = None,
      categories = List(ApiCategory.OTHER)
    )

  }

  "save" should {
    "insert an Api definition with 1 version when it does not exist" in new Setup {
      await(serviceRepo.save(definition))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldEqual definition
    }

    "insert an Api definition with 3 version when it does not exist" in new Setup {
      val threeVersionDefinition = definition.copy(versions = List(testApiVersion1, testApiVersion2, testApiVersion3))
      await(serviceRepo.save(threeVersionDefinition))

      val fetchedRecords = await(serviceRepo.collection.withReadPreference(primaryPreferred()).find().toFuture())

      fetchedRecords.size shouldBe 1
      fetchedRecords.head shouldEqual threeVersionDefinition
    }
  }
}
