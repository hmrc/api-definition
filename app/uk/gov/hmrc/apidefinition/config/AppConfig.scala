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

package uk.gov.hmrc.apidefinition.config

import javax.inject.{Inject, Singleton}

import net.ceedubs.ficus.Ficus._

import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (val runModeConfiguration: Configuration, environment: Environment, servicesConfig: ServicesConfig) {
  def mode: Mode = environment.mode

  lazy val buildProductionUrlForPrototypedAPIs: Boolean = runModeConfiguration.getOptional[Boolean]("buildProductionUrlForPrototypedAPIs").getOrElse(false)
  lazy val isSandbox: Boolean                           = runModeConfiguration.getOptional[Boolean]("isSandbox").getOrElse(false)

  lazy val fetchByContextTtlInSeconds: String = runModeConfiguration.underlying.as[String]("fetchByContextTtlInSeconds")

  lazy val ramlLoaderRewrites = buildRamlLoaderRewrites

  lazy val serviceBaseUrl = runModeConfiguration.getOptional[String]("serviceBaseUrl").getOrElse("http://localhost")

  lazy val apisToRemove = runModeConfiguration.underlying.as[List[String]]("apisToRemove")

  lazy val apisToRetire: Seq[String] = runModeConfiguration.underlying.as[Option[List[String]]]("apisToRetire").getOrElse(List.empty)

  lazy val skipContextValidationAllowlist: List[ServiceName] = runModeConfiguration.underlying.as[List[String]]("skipContextValidationAllowlist").map(ServiceName(_))

  def baseUrl(serviceName: String): String = {
    val context = runModeConfiguration.getOptional[String](s"$serviceName.context").getOrElse("")

    if (context.length > 0) s"${servicesConfig.baseUrl(serviceName)}/$context"
    else servicesConfig.baseUrl(serviceName)
  }

  private def buildRamlLoaderRewrites: Map[String, String] = {
    Map(
      runModeConfiguration.getOptional[String](s"ramlLoaderUrlRewrite.from").getOrElse("") ->
        runModeConfiguration.getOptional[String](s"ramlLoaderUrlRewrite.to").getOrElse("")
    )
  }
}
