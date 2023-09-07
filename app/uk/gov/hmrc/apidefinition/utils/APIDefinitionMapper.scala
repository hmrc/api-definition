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

package uk.gov.hmrc.apidefinition.utils

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, _}

import uk.gov.hmrc.apidefinition.config.AppConfig

@Singleton
class APIDefinitionMapper @Inject() (val appContext: AppConfig) {

  val buildProductionUrlsForPrototypedAPIs = appContext.buildProductionUrlForPrototypedAPIs

  def mapLegacyStatuses(apiDefinition: ApiDefinition): ApiDefinition = {

    def mapVersion(version: ApiVersion): ApiVersion = {
      version.status match {
        case ApiStatus.PROTOTYPED =>
          version.copy(status = ApiStatus.BETA, endpointsEnabled = version.endpointsEnabled.orElse(Some(buildProductionUrlsForPrototypedAPIs)))
        case ApiStatus.PUBLISHED  =>
          version.copy(status = ApiStatus.STABLE, endpointsEnabled = version.endpointsEnabled.orElse(Some(true)))
        case ApiStatus.DEPRECATED =>
          version.copy(status = ApiStatus.DEPRECATED, endpointsEnabled = version.endpointsEnabled.orElse(Some(true)))
        case ApiStatus.RETIRED    =>
          version.copy(status = ApiStatus.RETIRED, endpointsEnabled = version.endpointsEnabled.orElse(Some(true)))
        case _                    => version
      }
    }

    apiDefinition.copy(versions = apiDefinition.versions.map(mapVersion))
  }

}
