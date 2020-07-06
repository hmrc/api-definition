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
import scala.concurrent.Future
import play.api.mvc.ControllerComponents
import play.api.mvc.Action
import play.api.mvc.AnyContent
import uk.gov.hmrc.apidocumentation.services.SchemaService
import uk.gov.hmrc.ramltools.loaders.RamlLoader
import scala.util.Failure
import scala.util.Success

@Singleton
class SpecificationController @Inject()(schemaService: SchemaService, ramlLoader: RamlLoader, cc: ControllerComponents)
                                       (implicit val ec: ExecutionContext)
                                        extends BackendController(cc) {

  def fetchSpecification(serviceName: String, version: String): Action[AnyContent] = Action.async {
    implicit request => {

      // val serviceBaseUrl = "http://localhost:9604"
      // val rootRamlUrl = s"${serviceLocation.serviceUrl}/api/conf/$version/application.raml")
      // ramlLoader.load(

      // Future.fromTry(ramlLoader.load(rootRamlUrl))
      //   .map(raml => Ok("Raml loaded: " + raml.description()))
      // Logger.info(s"API Documentation received request for resource: $serviceName, $version, $resource")      
      
      // TODO: serviceBaseUrl
      val serviceBaseUrl = "http://localhost:9604"
      val rootRamlUrl = serviceBaseUrl + routes.DocumentationController.fetchApiDocumentationResource(serviceName,version, "application.raml").url
      Future.fromTry(ramlLoader.load(rootRamlUrl))
        .map(raml => Ok("Raml loaded: " + raml.title().value()))
      
        // Logger.info(s"API Documentation received request for resource: $serviceName, $version, $resource")      
    }
  }
}
