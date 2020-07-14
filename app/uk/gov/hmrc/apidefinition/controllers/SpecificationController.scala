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
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext
import play.api.mvc.ControllerComponents
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.libs.json.Json
import uk.gov.hmrc.apidefinition.services.SpecificationService
import uk.gov.hmrc.apidefinition.config.AppConfig

@Singleton
class SpecificationController @Inject()(specificationService: SpecificationService, config: AppConfig, cc: ControllerComponents)
                                       (implicit val ec: ExecutionContext)
                                        extends BackendController(cc) {

  

  def fetchSpecification(serviceName: String, version: String): Action[AnyContent] = Action.async {
    _ => {
      specificationService.fetchSpecification(serviceName, version)
        .map(json => Ok(Json.prettyPrint(json)))
    }
  }
}
