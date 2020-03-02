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

package uk.gov.hmrc.apidefinition.connector

import java.util.UUID

import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.apidefinition.models.Application
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThirdPartyApplicationConnector @Inject()(http: HttpClient,
                                               environment: Environment,
                                               val runModeConfiguration: Configuration,
                                               servicesConfig: ServicesConfig)
                                              (implicit val ec: ExecutionContext){

 protected val mode: Mode = environment.mode
  lazy val serviceUrl: String = servicesConfig.baseUrl("third-party-application")

  def fetchApplicationsByEmail(email: String)(implicit hc: HeaderCarrier): Future[Seq[Application]] = {
    http.GET[Seq[Application]](s"$serviceUrl/application", Seq("emailAddress" -> email))
  }

  def fetchSubscribers(context: String, version: String)(implicit hc: HeaderCarrier): Future[Seq[UUID]] = {
    http.GET(s"$serviceUrl/apis/$context/versions/$version/subscribers") map { result =>
      (result.json \ "subscribers").as[Seq[UUID]]
    }
  }
}
