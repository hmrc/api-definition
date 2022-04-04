/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.apidefinition.models.ErrorCode
import uk.gov.hmrc.apidefinition.services.DocumentationService
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class DocumentationController @Inject()(service: DocumentationService, cc: ControllerComponents)
                                       (implicit val ec: ExecutionContext)
                                        extends BackendController(cc) with ApplicationLogger {

  def fetchApiDocumentationResource(serviceName: String, version: String, resource: String): Action[AnyContent] = Action.async {
    _ => {
      logger.info(s"API Documentation received request for resource: $serviceName, $version, $resource")
      service.fetchApiDocumentationResource(serviceName, version, resource)
    } recover recovery
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case e: NotFoundException =>
      logger.info(s"Unable to fetch resource: ${e.getMessage}", e)
      NotFound(error(ErrorCode.API_DEFINITION_NOT_FOUND, e.getMessage))
    case NonFatal(e) =>
      logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage))
  }
}
