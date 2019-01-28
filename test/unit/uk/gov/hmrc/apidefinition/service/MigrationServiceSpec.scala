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

package unit.uk.gov.hmrc.apidefinition.service

import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import org.scalatest.BeforeAndAfterEach
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.MigrationService
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper

import scala.concurrent.ExecutionContext.Implicits.global

class MigrationServiceSpec extends UnitSpec
  with MongoSpecSupport with WithFakeApplication with BeforeAndAfterEach {

  private def version(version: String, status: APIStatus) = {
    APIVersion(version, status, None,
      Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
      Some(true)
    )
  }

  private def definition(serviceName: String, name: String, context: String, versions: Seq[APIVersion]) = {
    APIDefinition(serviceName, serviceBaseUrl = s"http://$name.url", name, description = "API description", context, versions, None)
  }

  val publishedApi = definition("published", "published", "published", Seq(version("1.0", APIStatus.PUBLISHED)))
  val prototypedApi = definition("prototyped", "prototyped", "prototyped", Seq(version("1.0", APIStatus.PROTOTYPED)))
  val betaApi = definition("beta", "beta", "beta", Seq(version("1.0", APIStatus.BETA)))

  val reactiveMongoComponent = new ReactiveMongoComponent { override def mongoConnector: MongoConnector = mongoConnectorForTest }
  val repository = new APIDefinitionRepository(reactiveMongoComponent)
  val apiDefinitionMapper = fakeApplication.injector.instanceOf[APIDefinitionMapper]

  override def beforeEach(): Unit = {
    await(repository.drop)
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    await(repository.drop)
    super.afterAll()
  }

  private trait Setup {
    val underTest = new MigrationService(repository, apiDefinitionMapper)
  }

  "MigrationService" should {
    "migrate legacy statuses and re-save the changed API definitions" in new Setup {
      await(repository.save(publishedApi))
      await(repository.save(prototypedApi))
      await(repository.save(betaApi))

      await(underTest.migrate())

      val migratedPublishedApi = await(repository.fetchByServiceName(publishedApi.serviceName))
      migratedPublishedApi.get.versions.head.status shouldBe APIStatus.STABLE

      val migratedPrototypedApi = await(repository.fetchByServiceName(prototypedApi.serviceName))
      migratedPrototypedApi.get.versions.head.status shouldBe APIStatus.BETA
    }
  }

}
