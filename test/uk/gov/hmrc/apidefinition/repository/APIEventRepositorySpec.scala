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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.Eventually
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiAccess.{PUBLIC, Private}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus.{ALPHA, BETA}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import uk.gov.hmrc.apidefinition.models.ApiEvents._
import uk.gov.hmrc.apidefinition.models.{ApiEvent, ApiEventId}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class APIEventRepositorySpec extends AsyncHmrcSpec
    with DefaultPlayMongoRepositorySupport[ApiEvent]
    with GuiceOneAppPerSuite with BeforeAndAfterEach
    with BeforeAndAfterAll with Eventually with FixedClock {

  override implicit lazy val app: Application = appBuilder.build()

  override val repository: APIEventRepository = app.injector.instanceOf[APIEventRepository]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  val version1                = ApiVersionNbr("1.0")
  val serviceName             = ServiceName("api-event-test")
  val apiName                 = "Api 123"
  val apiCreated              = ApiCreated(ApiEventId.random, apiName, serviceName, instant)
  val newApiVersion           = NewApiVersion(ApiEventId.random, apiName, serviceName, instant, ALPHA, version1)
  val apiVersionStatusChange  = ApiVersionStatusChange(ApiEventId.random, apiName, serviceName, instant, ALPHA, BETA, version1)
  val apiVersionAccessChange  = ApiVersionAccessChange(ApiEventId.random, apiName, serviceName, instant, PUBLIC, Private(true), version1)
  val publishedNoChange       = ApiPublishedNoChange(ApiEventId.random, apiName, serviceName, instant)
  val apiList: List[ApiEvent] = List(apiCreated, newApiVersion, apiVersionStatusChange, apiVersionAccessChange, publishedNoChange)

  "createEvent()" should {
    val eventTable = Table("Event to use", apiList: _*)

    "create a Api Event in Mongo" in forAll(eventTable) { event =>
      {
        val result = await(repository.createEvent(event))
        result shouldBe true
        await(repository.collection.find(Filters.equal("id", Codecs.toBson(event.id))).toFuture()) shouldBe List(event)
      }
    }
  }

  "createAll()" should {
    "create all Api Events in Mongo" in {
      await(repository.createAll(apiList))
      val result = await(repository.fetchEvents(serviceName))
      result shouldBe apiList
    }
  }

  "fetchEvents" should {
    "fetch all events by serviceName" in {
      await(Future.sequence(apiList.map(repository.collection.insertOne(_).toFuture())))
      await(repository.collection.insertOne(apiCreated.copy(id = ApiEventId.random, serviceName = ServiceName("OTHER"))).toFuture())
      val result = await(repository.fetchEvents(serviceName))
      result shouldBe apiList
    }
  }

}
