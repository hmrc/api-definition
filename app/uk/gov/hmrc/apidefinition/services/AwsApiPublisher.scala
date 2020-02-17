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

package uk.gov.hmrc.apidefinition.services

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models.AWSAPIDefinition.awsApiGatewayName
import uk.gov.hmrc.apidefinition.models.{APIDefinition, APIStatus, APIVersion}
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.AWSPayloadHelper.buildAWSSwaggerDetails
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.sequence
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex

@Singleton
class AwsApiPublisher @Inject()(val awsAPIPublisherConnector: AWSAPIPublisherConnector, val apiDefinitionRepository: APIDefinitionRepository)
                               (implicit val ec: ExecutionContext) {

  val hostRegex: Regex = "https?://(.+)".r
  val IgnoredContexts: Seq[String] = Seq("sso-in", "web-session")

  def publishAll(apiDefinitions: Seq[APIDefinition])(implicit hc: HeaderCarrier): Unit = {
    apiDefinitions.foreach(publish)
  }

  def publish(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (IgnoredContexts.contains(apiDefinition.context)) {
      Logger.info(s"Ignoring publishing of ${apiDefinition.context}")
      Future.successful(())
    } else {
      sequence {
        apiDefinition.versions.map { apiVersion =>
          val apiName = awsApiGatewayName(apiVersion.version, apiDefinition)

          apiVersion.status match {
            case APIStatus.RETIRED => deleteAPIVersion(apiName)
            case _ => publishAPIVersion(apiName, apiDefinition.name, apiDefinition.serviceBaseUrl, apiDefinition.context, apiVersion)
          }
        }
      } map { _ =>
        Logger.info(s"Successfully published API '${apiDefinition.serviceName}' to AWS API Gateway")
      } recover {
        case NonFatal(e) =>
          Logger.error(s"Failed to publish API '${apiDefinition.serviceName}' to AWS API Gateway", e)
      }
    }
  }

  private def publishAPIVersion(apiName: String,
                        apiDefinitionName: String,
                        serviceBaseUrl: String,
                        context: String,
                        apiVersion: APIVersion)(implicit hc: HeaderCarrier): Future[Unit] = {
    val hostRegex(host) = serviceBaseUrl
    val swagger = buildAWSSwaggerDetails(apiDefinitionName, apiVersion, context, host)
    awsAPIPublisherConnector.createOrUpdateAPI(apiName, swagger)(hc)
      .map(awsRequestId => Logger.info(s"Successfully published API [$apiName] Version [${apiVersion.version}] under AWS Request Id [$awsRequestId]"))
  }

  def delete(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (IgnoredContexts.contains(apiDefinition.context)) {
      Logger.info(s"Ignoring deletion of ${apiDefinition.context}")
      Future.successful(())
    } else {
      sequence {
        apiDefinition.versions.map { apiVersion =>
          deleteAPIVersion(awsApiGatewayName(apiVersion.version, apiDefinition))
        }
      } map {_ =>
        Logger.info(s"Successfully deleted all versions for API '${apiDefinition.serviceName}' from AWS API Gateway")
      } recover {
        case NonFatal(e) => Logger.error(s"Failed to delete API '${apiDefinition.serviceName}' from AWS API Gateway", e)
      }
    }
  }

  private def deleteAPIVersion(apiName: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    awsAPIPublisherConnector.deleteAPI(apiName)(hc) map { requestId =>
      Logger.info(s"Successfully deleted API '$apiName' from AWS API Gateway with request ID $requestId")
    }
  }
}
