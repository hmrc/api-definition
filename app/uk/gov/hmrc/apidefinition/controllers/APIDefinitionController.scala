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

package uk.gov.hmrc.apidefinition.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{StoredApiDefinition, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext
import uk.gov.hmrc.http.UnauthorizedException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.ErrorCode._
import uk.gov.hmrc.apidefinition.models.{DisplayApiEvent, ErrorCode, TolerantJsonApiDefinition}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import uk.gov.hmrc.apidefinition.validators.ApiDefinitionValidator

@Singleton
class APIDefinitionController @Inject() (
    apiDefinitionValidator: ApiDefinitionValidator,
    apiDefinitionService: APIDefinitionService,
    appContext: AppConfig,
    cc: ControllerComponents
  )(implicit val ec: ExecutionContext
  ) extends BackendController(cc) with ApplicationLogger {

  implicit val useTolerantReaders: Format[StoredApiDefinition] = TolerantJsonApiDefinition.tolerantFormatApiDefinition

  val fetchByContextTtlInSeconds: String = appContext.fetchByContextTtlInSeconds

  def createOrUpdate(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    implicit val useTolerantReaders = TolerantJsonApiDefinition.tolerantFormatApiDefinition

    handleRequest[StoredApiDefinition](request) { requestBody =>
      apiDefinitionValidator.validate(requestBody) { validatedDefinition =>
        logger.info(s"Create/Update API definition request: $validatedDefinition")
        apiDefinitionService.createOrUpdate(validatedDefinition).map { _ =>
          logger.info("API definition successfully created/updated")
          NoContent
        } recover recovery
      }
    }
  }

  def delete(serviceName: ServiceName): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.delete(serviceName).map { _ => NoContent } recover {
      case e: UnauthorizedException => Forbidden(e.getMessage)
    } recover recovery
  }

  def fetch(serviceName: ServiceName): Action[AnyContent] = Action.async { _ =>
    apiDefinitionService.fetchByServiceName(serviceName) map {
      case Some(apiDefinition) => Ok(Json.toJson(apiDefinition))
      case _                   => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case NonFatal(e) =>
      logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage))
  }

  def validate: Action[JsValue] = Action.async(parse.json) { implicit request =>
    handleRequest[StoredApiDefinition](request) { requestBody =>
      apiDefinitionValidator.validate(requestBody) { validatedDefinition =>
        successful(Accepted(Json.toJson(validatedDefinition)))
      }
    }
  }

  def queryDispatcher(): Action[AnyContent] = Action.async { implicit request =>
    val queryParameters: Seq[(String, String)] = request.queryString.toList.map { case (key, values) => (key, values.head) }.sorted

    val options                            = extractQueryOptions(request)
    lazy val errorInParams: Future[Result] = successful(BadRequest("Invalid query parameter or parameters"))

    queryParameters match {
      case Nil | ("options", _) :: Nil                  => fetchAllPublicAPIs(options.alsoIncludePrivateTrials)
      case ("context", context) :: Nil                  => fetchByContext(ApiContext(context))
      case ("type", typeValue) :: Nil                   => fetchDefinitionsByType(typeValue, options.alsoIncludePrivateTrials)
      case ("options", _) :: ("type", typeValue) :: Nil => fetchDefinitionsByType(typeValue, options.alsoIncludePrivateTrials)
      case _                                            => errorInParams
    }
  }

  def publishAllToAws(): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.publishAllToAws().map { _ => NoContent } recover recovery
  }

  def fetchEvents(serviceName: ServiceName, includeNoChange: Boolean = true): Action[AnyContent] = Action.async { _ =>
    apiDefinitionService.fetchEventsByServiceName(serviceName, includeNoChange) map { apiEvents =>
      Ok(Json.toJson(apiEvents.map(DisplayApiEvent(_))))
    } recover recovery
  }

  def deleteEvents(serviceName: ServiceName): Action[AnyContent] = Action.async { _ =>
    apiDefinitionService.deleteEventsByServiceName(serviceName) map { _ =>
      Ok("")
    } recover recovery
  }

  private def extractQueryOptions(request: Request[AnyContent]) = {
    QueryOptions(request.getQueryString("options"))
  }

  private def apiDefinitionToResult(result: List[ApiDefinition]) = {
    Ok(Json.toJson(result))
  }

  private def fetchAllPrivateAPIs() = {
    apiDefinitionService.fetchAllPrivateAPIs()
      .map(apiDefinitionToResult) recover recovery
  }

  private def fetchAllPublicAPIs(alsoIncludePrivateTrials: Boolean) = {
    apiDefinitionService
      .fetchAllPublicAPIs(alsoIncludePrivateTrials)
      .map(apiDefinitionToResult) recover recovery
  }

  private def fetchAll: Future[Result] = {
    apiDefinitionService.fetchAll.map(apiDefinitionToResult) recover recovery
  }

  private def fetchByContext(context: ApiContext) = {
    apiDefinitionService
      .fetchByContext(context).map {
        case Some(api) => Ok(Json.toJson(api)).withHeaders(HeaderNames.CACHE_CONTROL -> s"max-age=$fetchByContextTtlInSeconds")
        case _         => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
      } recover recovery
  }

  private def fetchDefinitionsByType(typeParam: String, alsoIncludePrivateTrials: Boolean) = {
    typeParam match {
      case "public"  => fetchAllPublicAPIs(alsoIncludePrivateTrials)
      case "private" => fetchAllPrivateAPIs()
      case "all"     => fetchAll
      case _         => Future(BadRequest(error(UNSUPPORTED_ACCESS_TYPE, s"$typeParam is not a supported access type")))
    }
  }
}
