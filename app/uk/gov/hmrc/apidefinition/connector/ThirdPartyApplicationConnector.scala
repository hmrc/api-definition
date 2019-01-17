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

package uk.gov.hmrc.apidefinition.connector

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidefinition.config.WSHttp
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apidefinition.models.Application
import uk.gov.hmrc.play.config.inject.DefaultServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class ThirdPartyApplicationConnector @Inject()(servicesConfig: DefaultServicesConfig, http: WSHttp) {

  lazy val serviceUrl = servicesConfig.baseUrl("third-party-application")

  def fetchApplicationsByEmail(email: String)(implicit hc: HeaderCarrier): Future[Seq[Application]] = {
    http.GET[Seq[Application]](s"$serviceUrl/application", Seq("emailAddress" -> email))
  }
}
