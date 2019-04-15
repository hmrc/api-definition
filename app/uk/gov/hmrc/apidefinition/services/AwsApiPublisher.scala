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

package uk.gov.hmrc.apidefinition.services

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.models.WSO2APIDefinition.wso2ApiName
import uk.gov.hmrc.apidefinition.utils.WSO2PayloadHelper.buildAWSSwaggerDetails
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.sequence
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class AwsApiPublisher @Inject()(val awsAPIPublisherConnector: AWSAPIPublisherConnector)(implicit val ec: ExecutionContext) {

  val hostIndex: Int = 8

  def publish(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[APIDefinition] = {
    sequence {
      apiDefinition.versions.map { apiVersion =>
        val swagger = buildAWSSwaggerDetails(wso2ApiName(apiVersion.version, apiDefinition),
          apiVersion, apiDefinition.context, apiDefinition.serviceBaseUrl.substring(hostIndex))

        apiVersion.awsApiId match {
          case Some(apiId) => awsAPIPublisherConnector.updateAPI(apiId, swagger)(hc).map(_ => apiVersion)
          case None => awsAPIPublisherConnector.createAPI(swagger)(hc).map(s => apiVersion.copy(awsApiId = Some(s)))
        }
      }
    } map(v => apiDefinition.copy(versions = v)) recover {
      case NonFatal(e) =>
        Logger.error("Failed to publish to AWS Gateway", e)
        apiDefinition
    }
  }
}
