/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.definition

import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Application, Mode}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.StoredApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository

trait ComponentSpec
    extends HmrcSpec
    with DefaultAwaitTimeout
    with FutureAwaits
    with DefaultPlayMongoRepositorySupport[StoredApiDefinition]
    with WireMockSupport
    with GuiceOneServerPerSuite {
  this: TestSuite =>
  override val repository: APIDefinitionRepository = app.injector.instanceOf[APIDefinitionRepository]

  override def commonStubs(): Unit = {}

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .configure(
      "mongodb.uri"                            -> mongoUri,
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
