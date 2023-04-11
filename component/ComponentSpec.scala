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
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

trait ComponentSpec extends AsyncHmrcSpec
  with DefaultPlayMongoRepositorySupport[APIDefinition] with BeforeAndAfterAll with GuiceOneServerPerSuite {

  override def repository: APIDefinitionRepository = app.injector.instanceOf[APIDefinitionRepository]

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .configure("mongodb.uri" -> mongoUri)
    .build()

  def get(endpoint: String): WSResponse = {
    val wsClient = app.injector.instanceOf[WSClient]
    await(wsClient.url(s"http://localhost:$port$endpoint").get())
  }
}
