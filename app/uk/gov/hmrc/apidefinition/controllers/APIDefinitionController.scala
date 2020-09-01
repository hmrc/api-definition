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

package uk.gov.hmrc.apidefinition.controllers

import javax.inject.{Inject, Singleton}
import play.api._
import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.ErrorCode._
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.models.{APIDefinition, ErrorCode}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper
import uk.gov.hmrc.apidefinition.validators.ApiDefinitionValidator
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import _root_.uk.gov.hmrc.apidefinition.models.APICategory

@Singleton
class APIDefinitionController @Inject()(apiDefinitionValidator: ApiDefinitionValidator,
                                        apiDefinitionService: APIDefinitionService,
                                        apiDefinitionMapper: APIDefinitionMapper,
                                        appContext: AppConfig,
                                        playBodyParsers: PlayBodyParsers,
                                        cc: ControllerComponents)
                                       (implicit val ec: ExecutionContext) extends BackendController(cc) {

  val fetchByContextTtlInSeconds: String = appContext.fetchByContextTtlInSeconds

  def createOrUpdate(): Action[JsValue] = Action.async(playBodyParsers.json) { implicit request =>
    handleRequest[APIDefinition](request) { requestBody =>
      apiDefinitionValidator.validate(requestBody) { validatedDefinition =>
        Logger.info(s"Create/Update API definition request: $validatedDefinition")
        apiDefinitionService.createOrUpdate(apiDefinitionMapper.mapLegacyStatuses(validatedDefinition)).map { _ =>
          Logger.info("API definition successfully created/updated")
          NoContent
        } recover recovery
      }
    }
  }

  def delete(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.delete(serviceName).map { _ => NoContent } recover {
      case e: UnauthorizedException => Forbidden(e.getMessage)
    } recover recovery
  }

  def fetchExtended(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.fetchExtendedByServiceName(serviceName, request.queryString.get("email").flatMap(_.headOption)) map {
      case Some(extendedApiDefinition) => Ok(Json.toJson(extendedApiDefinition))
      case _ => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery
  }

  def fetch(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.fetchByServiceName(serviceName) map {
      case Some(apiDefinition) => Ok(Json.toJson(apiDefinition))
      case _ => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery
  }

  private def recovery: PartialFunction[Throwable, Result] = {
    case e =>
      Logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(error(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage))
  }

  def validate: Action[JsValue] = Action.async(playBodyParsers.json) { implicit request =>
    handleRequest[APIDefinition](request) { requestBody =>
      apiDefinitionValidator.validate(requestBody) { validatedDefinition =>
        Future.successful(Accepted(Json.toJson(validatedDefinition)))
      }
    }
  }

  def queryDispatcher(): Action[AnyContent] = Action.async { implicit request =>

    val queryParameters: Seq[(String, String)] = request.queryString.toList.map { case (key, values) => (key, values.head) }.sorted

    val options = extractQueryOptions(request)

    queryParameters match {
      case Nil | ("options", _) :: Nil => fetchAllPublicAPIs(options.alsoIncludePrivateTrials)
      case ("context", context) :: Nil => fetchByContext(context)
      case ("applicationId", applicationId) :: _ => fetchAllForApplication(applicationId, options.alsoIncludePrivateTrials)
      case ("email", email) :: _ => fetchAllForCollaborator(email, options.alsoIncludePrivateTrials)
      case ("type", typeValue) :: Nil => fetchDefinitionsByType(typeValue, options.alsoIncludePrivateTrials)
      case ("options", _) :: ("type", typeValue) :: Nil => fetchDefinitionsByType(typeValue, options.alsoIncludePrivateTrials)
      case _ => Future.successful(BadRequest("Invalid query parameter or parameters"))
    }
  }

  def publishAllToAws(): Action[AnyContent] = Action.async { implicit request =>
    apiDefinitionService.publishAllToAws().map { _ => NoContent } recover recovery
  }

  def fetchAllAPICategories: Action[AnyContent] = Action.async {
    Future.successful(Ok(Json.toJson(APICategory.allAPICategoryDetails)))
  }

  private def extractQueryOptions(request: Request[AnyContent]) = {
    QueryOptions(request.getQueryString("options"))
  }

  private def apiDefinitionToResult(result: Seq[APIDefinition]) = {
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

  private def fetchByContext(context: String) = {
    apiDefinitionService
      .fetchByContext(context).map {
      case Some(api) => Ok(Json.toJson(api)).withHeaders(HeaderNames.CACHE_CONTROL -> s"max-age=$fetchByContextTtlInSeconds")
      case _ => NotFound(error(API_DEFINITION_NOT_FOUND, "No API Definition was found"))
    } recover recovery
  }

  private def fetchAllForApplication(applicationId: String, alsoIncludePrivateTrials: Boolean) = {
    apiDefinitionService
      .fetchAllAPIsForApplication(applicationId, alsoIncludePrivateTrials)
      .map(apiDefinitionToResult) recover recovery
  }

  private def fetchAllForCollaborator(email: String, alsoIncludePrivateTrials: Boolean)(implicit hc: HeaderCarrier) = {
    apiDefinitionService
      .fetchAllAPIsForCollaborator(email, alsoIncludePrivateTrials)
      .map(apiDefinitionToResult) recover recovery
  }

  private def fetchDefinitionsByType(typeParam: String, alsoIncludePrivateTrials: Boolean) = {
    typeParam match {
      case "public" => fetchAllPublicAPIs(alsoIncludePrivateTrials)
      case "private" => fetchAllPrivateAPIs()
      case "all" => fetchAll
      case _ => Future(BadRequest(error(UNSUPPORTED_ACCESS_TYPE, s"$typeParam is not a supported access type")))
    }
  }
}
