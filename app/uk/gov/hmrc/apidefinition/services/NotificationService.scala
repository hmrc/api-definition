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

import play.api.Logger
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus

import scala.concurrent.{ExecutionContext, Future}

trait NotificationService {
  def notifyOfStatusChange(apiName: String, apiVersion: String, existingAPIStatus: APIStatus, newAPIStatus: APIStatus)
                          (implicit ec: ExecutionContext): Future[Unit]
}

class LoggingNotificationService extends NotificationService {

  def notifyOfStatusChange(apiName: String, apiVersion: String, existingAPIStatus: APIStatus, newAPIStatus: APIStatus)
                          (implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      Logger.info(s"API [$apiName] Version [$apiVersion] Status has changed from [$existingAPIStatus] to [$newAPIStatus]")
    }
  }
}

class EmailNotificationService(val emailAddresses: Set[String]) extends NotificationService {
  override def notifyOfStatusChange(apiName: String, apiVersion: String, existingAPIStatus: APIStatus, newAPIStatus: APIStatus)
                                   (implicit ec: ExecutionContext): Future[Unit] = {
    Future.successful()
  }
}
