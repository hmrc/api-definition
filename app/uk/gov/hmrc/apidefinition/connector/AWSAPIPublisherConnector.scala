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

import javax.inject.Inject

import play.api.Mode.Mode
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.models.WSO2SwaggerDetails
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.apidefinition.models.JsonFormatters._

import scala.concurrent.{ExecutionContext, Future}

class AWSAPIPublisherConnector @Inject()(http: HttpClient,
                                         environment: Environment,
                                         override val runModeConfiguration: Configuration)
                                        (implicit val ec: ExecutionContext) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  val serviceBaseUrl = baseUrl("aws-gateway")

  def createAPI(wso2SwaggerDetails: WSO2SwaggerDetails)(implicit hc: HeaderCarrier): Future[String] = {
    http.POST(serviceBaseUrl, Json.toJson(wso2SwaggerDetails), Seq(CONTENT_TYPE -> JSON)) map { result =>
      (result.json \ "restApiId").as[String]
    }
  }

  def updateAPI(awsApiId: String, wso2SwaggerDetails: WSO2SwaggerDetails)(implicit hc: HeaderCarrier): Future[String] = {
    http.PUT(s"$serviceBaseUrl/$awsApiId", Json.toJson(wso2SwaggerDetails)) map { result => (result.json \ "restApiId").as[String] }
  }
}
