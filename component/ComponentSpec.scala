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

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Application, Mode}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

trait ComponentSpec extends AsyncHmrcSpec
  with DefaultPlayMongoRepositorySupport[ApiDefinition] with WireMockSupport with GuiceOneServerPerSuite  {

  override def repository: APIDefinitionRepository = app.injector.instanceOf[APIDefinitionRepository]

   override def commonStubs(): Unit = {}

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .configure(
      "mongodb.uri" -> mongoUri,
      "microservice.services.aws-gateway.host" -> wireMockHost,
      "microservice.services.aws-gateway.port" -> wireMockPort
    )
    .build()

  val wsClient = app.injector.instanceOf[WSClient]

  def get(endpoint: String): WSResponse = {
    await(wsClient.url(s"http://localhost:$port$endpoint").get())
  }

  def post(endpoint: String, body: String, headers: List[(String, String)]): WSResponse = {
      await(
        wsClient
      .url(s"http://localhost:$port$endpoint")
      .withHttpHeaders(headers: _*)
      .withFollowRedirects(false)
      .post(body)
      )
  }
}
