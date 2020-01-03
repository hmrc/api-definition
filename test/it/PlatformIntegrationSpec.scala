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

package it

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Mode}
import uk.gov.hmrc.apidefinition.controllers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

trait PlatformIntegrationSpec extends UnitSpec with GuiceOneAppPerSuite {

  implicit def mat: akka.stream.Materializer = app.injector.instanceOf[akka.stream.Materializer]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val publishApiDefinition: Boolean

  override def fakeApplication(): Application =
    GuiceApplicationBuilder().configure(Map("publishApiDefinition" -> publishApiDefinition)).in(Mode.Test).build()

  trait Setup {
    val controller = app.injector.instanceOf[APIDefinitionController]
  }
}

class PublishApiDefinitionEnabledSpec extends PlatformIntegrationSpec {
  val publishApiDefinition = true

  "microservice" should {

    "return the JSON definition" in new Setup {
      route(app, FakeRequest(GET, "/api/definition")) match {
        case Some(resultF) =>
          val result = await(resultF)
          status(result) shouldBe OK
          bodyOf(result) should include(""""context": "api-definition"""")

        case _ => fail
      }
    }

    "return the RAML" in new Setup {
      route(app, FakeRequest(GET, "/api/conf/1.0/application.raml")) match {
        case Some(resultF) =>
          val result = await(resultF)
          status(result) shouldBe OK
          bodyOf(result) should include("#%RAML 1.0")

        case _ => fail
      }
    }
  }
}

class PublishApiDefinitionDisabledSpec extends PlatformIntegrationSpec {
  val publishApiDefinition = false

  "microservice" should {

    "return the JSON definition" in new Setup {
      route(app, FakeRequest(GET, "/api/definition")) match {
        case Some(resultF) =>
          val result = await(resultF)
          status(result) shouldBe NO_CONTENT

        case _ => fail
      }
    }

    "return the RAML" in new Setup {
      route(app, FakeRequest(GET, "/api/conf/1.0/application.raml")) match {
        case Some(resultF) =>
          val result = await(resultF)
          status(result) shouldBe NO_CONTENT

        case _ => fail
      }
    }
  }
}
