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
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersionNbr
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.services.SpecificationService

@Singleton
class SpecificationController @Inject() (specificationService: SpecificationService, config: AppConfig, cc: ControllerComponents)(implicit val ec: ExecutionContext)
    extends BackendController(cc) {

  private def formatSpecificationResponse(in: Future[Option[JsValue]]) = {
    in.map {
      case None       => NotFound("RAML not found in this environment")
      case Some(json) => Ok(Json.prettyPrint(json))
    }
  }

  def fetchApiSpecification(serviceName: String, version: ApiVersionNbr): Action[AnyContent] = Action.async {
    _ =>
      {
        formatSpecificationResponse(specificationService.fetchApiSpecification(serviceName, version))
      }
  }

  def fetchPreviewApiSpecification(rootRamlUrl: String): Action[AnyContent] = Action.async {
    _ =>
      {
        formatSpecificationResponse(specificationService.fetchPreviewApiSpecification(rootRamlUrl))
      }
  }
}
