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
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.WSO2PayloadHelper.buildAWSSwaggerDetails
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.sequence
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex

@Singleton
class AwsApiPublisher @Inject()(val awsAPIPublisherConnector: AWSAPIPublisherConnector, val apiDefinitionRepository: APIDefinitionRepository)
                               (implicit val ec: ExecutionContext) {

  val hostRegex: Regex = "https?://(.+)".r

  def publishAll(apiDefinitions: Seq[APIDefinition])(implicit hc: HeaderCarrier): Unit = {
    apiDefinitions.foreach(publish)
  }

  def publish(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[APIDefinition] = {
    sequence {
      apiDefinition.versions.map { apiVersion =>
        val hostRegex(host) = apiDefinition.serviceBaseUrl
        val swagger = buildAWSSwaggerDetails(apiDefinition.name, apiVersion, apiDefinition.context, host)
        awsAPIPublisherConnector.createOrUpdateAPI(wso2ApiName(apiVersion.version, apiDefinition), swagger)(hc)
          .map(requestId => apiVersion.copy(awsRequestId = Some(requestId)))
      }
    } map { v =>
      Logger.info(s"Successfully published API '${apiDefinition.serviceName}' to AWS API Gateway")
      apiDefinition.copy(versions = v)
    } recover {
      case NonFatal(e) =>
        Logger.error(s"Failed to publish API '${apiDefinition.serviceName}' to AWS API Gateway", e)
        apiDefinition
    }
  }

  def delete(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    sequence {
      apiDefinition.versions.map { apiVersion =>
        val apiName = wso2ApiName(apiVersion.version, apiDefinition)
        awsAPIPublisherConnector.deleteAPI(apiName)(hc) map { requestId =>
          Logger.info(s"Successfully deleted API '$apiName' from AWS API Gateway with request ID $requestId")
        }
      }
    } map {_ =>
      Logger.info(s"Successfully deleted all versions for API '${apiDefinition.serviceName}' from AWS API Gateway")
    } recover {
      case NonFatal(e) => Logger.error(s"Failed to delete API '${apiDefinition.serviceName}' from AWS API Gateway", e)
    }
  }
}
