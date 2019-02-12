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

package uk.gov.hmrc.apidefinition.controllers

import javax.inject.{Inject, Singleton}
import play.api._
import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.models.ErrorCode._
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.models.{APIDefinition, ErrorCode}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper
import uk.gov.hmrc.apidefinition.validators.ApiDefinitionValidator
import uk.gov.hmrc.http.UnauthorizedException
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class APIDefinitionController @Inject()(apiDefinitionValidator: ApiDefinitionValidator,
                                        apiDefinitionService: APIDefinitionService,
                                        apiDefinitionMapper: APIDefinitionMapper,
                                        appContext: AppContext)
                                       (implicit val ec: ExecutionContext) extends BaseController {

  val fetchByContextTtlInSeconds: String = appContext.fetchByContextTtlInSeconds

  def createOrUpdate(): Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    handleRequest[APIDefinition](request) { requestBody =>
      apiDefinitionValidator.validate(requestBody) { validatedDefinition =>
        Logger.info(s"Create/Update API definition request: $validatedDefinition")
        apiDefinitionService.createOrUpdate(apiDefinitionMapper.mapLegacyStatuses(validatedDefinition)).map { result =>
          Logger.info(s"API definition successfully created/updated: $result")
          Ok(Json.toJson(result))
        } recover recovery
      }
    }
  }

  def delete(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.delete(serviceName).map { _ => NoContent } recover {
      case e: UnauthorizedException => Forbidden(e.getMessage)
    } recover recovery
  }

  def fetchExtended(serviceName: String):  Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.fetchExtendedByServiceName(serviceName, request.queryString.get("email").flatMap(_.headOption)) map {
      case Some(extendedApiDefinition) => Ok(Json.toJson(extendedApiDefinition))
      case _ => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery
  }

  def fetch(serviceName: String):  Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.fetchByServiceName(serviceName, request.queryString.get("email").flatMap(_.headOption)) map {
      case Some(apiDefinition) => Ok(Json.toJson(apiDefinition))
      case _ => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery
  }

  def validate: Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    handleRequest[APIDefinition](request) { requestBody =>
      apiDefinitionValidator.validate(requestBody) { validatedDefinition =>
        Future.successful(Accepted(Json.toJson(validatedDefinition)))
      }
    }
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case e =>
      Logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage))
  }

  def queryDispatcher():  Action[AnyContent] = Action.async { implicit request =>

    def getParameter(param: String): String = request.queryString(param).head

    def apiDefinitionToResult(result: Seq[APIDefinition]) = Ok(Json.toJson(result))

    def fetchAllPrivateAPIs() = apiDefinitionService.fetchAllPrivateAPIs()
      .map(apiDefinitionToResult) recover recovery

    def fetchAllPublicAPIs() = apiDefinitionService.fetchAllPublicAPIs()
      .map(apiDefinitionToResult) recover recovery

    def fetchByContext(context: String) = apiDefinitionService.fetchByContext(context).map {
      case Some(api) => Ok(Json.toJson(api)).withHeaders(HeaderNames.CACHE_CONTROL -> s"max-age=$fetchByContextTtlInSeconds")
      case _ => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery

    def fetchAllForApplication(applicationId: String) = apiDefinitionService.fetchAllAPIsForApplication(applicationId)
      .map(apiDefinitionToResult) recover recovery

    def fetchAllForCollaborator(email: String) = apiDefinitionService.fetchAllAPIsForCollaborator(email)
      .map(apiDefinitionToResult) recover recovery

    def fetchDefinitionsByType(typeParam: String) = {
      typeParam match {
        case "public" => fetchAllPublicAPIs()
        case "private" => fetchAllPrivateAPIs()
        case _ => Future(BadRequest(error(UNSUPPORTED_ACCESS_TYPE, s"$typeParam is not a supported access type")))
      }
    }

    request.queryString.keys.headOption match {
      case Some("context") => fetchByContext(getParameter("context"))
      case Some("applicationId") => fetchAllForApplication(getParameter("applicationId"))
      case Some("email") => fetchAllForCollaborator(getParameter("email"))
      case Some("type") => fetchDefinitionsByType(getParameter("type"))
      case _ => fetchAllPublicAPIs()
    }
  }

  def publishAll():  Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.publishAll().map { _ => NoContent } recover recovery
  }

}
