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

package uk.gov.hmrc.apidefinition.services

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

class ApiRemover(awsApiPublisherConnector: AWSAPIPublisherConnector, config: AppConfig) extends ApplicationLogger {
    
    def deleteUnusedApis()(implicit hc: HeaderCarrier, ec: ExecutionContext) : Unit = {
        logger.info(s"Attempting to delete ${config.apisToRemove.length} unused APIs.")
        config.apisToRemove.map{api => deleteApi(api)}
    }

    private def deleteApi(api: String)(implicit hc: HeaderCarrier, ec: ExecutionContext) : Unit = {
        awsApiPublisherConnector.deleteAPI(api).onComplete({
            case Success(requestId) => logger.info(s"$api successfully deleted. (Request ID: $requestId)")
            case Failure(exception) => logger.warn(s"$api delete failed.", exception)
        })
    }
}
