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

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.apidefinition.config.AppConfig

class ApiRemover(awsApiPublisherConnector: AWSAPIPublisherConnector, config: AppConfig) extends ApplicationLogger {
    
    def deleteUnusedApis()(implicit hc: HeaderCarrier, ec: ExecutionContext) : Unit = {

        def deleteUnusedApisHelper(api: String)(implicit hc: HeaderCarrier) : Unit = {
            awsApiPublisherConnector.deleteAPI(api).onComplete({
                case Success(requestId) => logger.info(s"$api successfully deleted. (Request ID: $requestId)")
                case Failure(exception) => logger.warn(s"$api delete failed.", exception)
            })
        }

        if(config.apisToRemove.isEmpty == false) {
            config.apisToRemove.map{api => deleteUnusedApisHelper(api)}
        } else { 
            logger.info(s"list of api's to remove is empty")
        }
    }
}
