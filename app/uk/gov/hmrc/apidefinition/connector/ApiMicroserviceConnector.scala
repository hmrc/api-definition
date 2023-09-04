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

package uk.gov.hmrc.apidefinition.connector

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.ws.{WSClient, WSResponse}

import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersionNbr

@Singleton
class ApiMicroserviceConnector @Inject() (ws: WSClient)(implicit val ec: ExecutionContext) extends ApplicationLogger {

  // TODO : Migrate to new hmrc WS client
  def fetchApiDocumentationResourceByUrl(serviceUrl: String, version: ApiVersionNbr, resource: String): Future[WSResponse] = {
    logger.info(s"Calling to local microservice to fetch documentation resource by URL: $serviceUrl, $version, $resource")
    ws.url(s"$serviceUrl/api/conf/$version/$resource").withMethod("GET").stream()
  }
}
