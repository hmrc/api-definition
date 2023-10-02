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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{blocking, ExecutionContext, Future}

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.ramltools.domain.RamlNotFoundException
import uk.gov.hmrc.ramltools.loaders.RamlLoader

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.controllers.routes
import uk.gov.hmrc.apidefinition.models.apispecification.ApiSpecificationFormatters._
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParser
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName

@Singleton
class SpecificationService @Inject() (config: AppConfig, ramlLoader: RamlLoader, apiSpecificationRamlParser: ApiSpecificationRamlParser)(implicit ec: ExecutionContext) {

  def fetchApiSpecification(serviceName: ServiceName, version: ApiVersionNbr): Future[Option[JsValue]] = {
    val rootRamlUrl = config.serviceBaseUrl + routes.DocumentationController.fetchApiDocumentationResource(serviceName, version, "application.raml").url
    fetchApiSpecificationAtUrl(rootRamlUrl)
  }

  def fetchPreviewApiSpecification(rootRamlUrl: String): Future[Option[JsValue]] = {
    // TODO - what security aspects do we need to implement?
    fetchApiSpecificationAtUrl(rootRamlUrl)
  }

  private def fetchApiSpecificationAtUrl(rootRamlUrl: String): Future[Option[JsValue]] = {
    val basePath = s"${rootRamlUrl.take(rootRamlUrl.lastIndexOf('/'))}/schemas"

    Future.fromTry {
      blocking {
        ramlLoader.load(rootRamlUrl)
          .map(raml => Json.toJson(apiSpecificationRamlParser.toApiSpecification(basePath, raml)))
      }
        .map(Some(_))
    }
      .recover {
        case _: RamlNotFoundException => None
      }
  }
}
