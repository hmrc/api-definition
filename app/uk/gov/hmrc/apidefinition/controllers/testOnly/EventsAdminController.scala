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

package uk.gov.hmrc.apidefinition.controllers.testOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apidefinition.controllers.error
import uk.gov.hmrc.apidefinition.models.{ErrorCode, TolerantJsonApiDefinition}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

@Singleton
class EventsAdminController @Inject() (
    apiDefinitionService: APIDefinitionService,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  implicit val useTolerantReaders: Format[StoredApiDefinition] = TolerantJsonApiDefinition.tolerantFormatApiDefinition

  private def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage))
  }

  def deleteEvents(serviceName: ServiceName): Action[AnyContent] = Action.async { _ =>
    apiDefinitionService.deleteEventsByServiceName(serviceName) map { _ =>
      Ok("")
    } recover recovery
  }
}
