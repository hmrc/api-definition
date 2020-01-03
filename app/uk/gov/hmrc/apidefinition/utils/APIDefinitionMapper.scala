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

package uk.gov.hmrc.apidefinition.utils

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models.{APIDefinition, APIStatus, APIVersion}

@Singleton
class APIDefinitionMapper @Inject()(val appContext: AppConfig) {

  val buildProductionUrlsForPrototypedAPIs = appContext.buildProductionUrlForPrototypedAPIs

  def mapLegacyStatuses(apiDefinition: APIDefinition): APIDefinition = {

    def mapVersion(version: APIVersion): APIVersion = {
      version.status match {
        case APIStatus.PROTOTYPED =>
          version.copy(status = APIStatus.BETA, endpointsEnabled = version.endpointsEnabled.orElse(Some(buildProductionUrlsForPrototypedAPIs)))
        case APIStatus.PUBLISHED =>
          version.copy(status = APIStatus.STABLE, endpointsEnabled = version.endpointsEnabled.orElse(Some(true)))
        case APIStatus.DEPRECATED =>
          version.copy(status = APIStatus.DEPRECATED, endpointsEnabled = version.endpointsEnabled.orElse(Some(true)))
        case APIStatus.RETIRED =>
          version.copy(status = APIStatus.RETIRED, endpointsEnabled = version.endpointsEnabled.orElse(Some(true)))
        case _ => version
      }
    }

    apiDefinition.copy(versions = apiDefinition.versions.map(mapVersion))
  }

}
