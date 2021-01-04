/*
 * Copyright 2021 HM Revenue & Customs
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

package component

import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Application, Mode}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

trait ComponentSpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterAll with GuiceOneServerPerSuite {
  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .configure("Test.mongodb.uri" -> mongoUri)
    .build()

  override protected def beforeAll(): Unit = {
    dropDb
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    dropDb
    super.afterAll()
  }

  def get(endpoint: String): WSResponse = {
    val wsClient = app.injector.instanceOf[WSClient]
    await(wsClient.url(s"http://localhost:$port$endpoint").get)
  }

  private def dropDb() = {
    await (
      new APIDefinitionRepository(new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }).drop
  )}
}
