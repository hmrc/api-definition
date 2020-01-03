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
import play.api.mvc.Action
import uk.gov.hmrc.apidefinition.models.APIAccess
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.views.txt
import uk.gov.hmrc.play.bootstrap.controller.BaseController


@Singleton
class PublishedDefinitionController @Inject()(config: DocumentationConfig) extends BaseController {

  def definition = Action {
    if (config.publishApiDefinition) {
      Ok(txt.definition(config)).withHeaders(CONTENT_TYPE -> JSON)
    } else {
      NoContent
    }
  }

  def raml(version: String, file: String) = Action {
    if (config.publishApiDefinition) {
      Ok(txt.application())
    } else {
      NoContent
    }
  }
}


case class DocumentationConfig(publishApiDefinition: Boolean, versions: Seq[ApiVersionConfig])

case class ApiVersionConfig(version: String, status: APIStatus, access: APIAccess, endpointsEnabled: Boolean)
