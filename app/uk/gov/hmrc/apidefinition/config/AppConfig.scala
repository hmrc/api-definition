/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class AppConfig @Inject()(override val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig {
  override protected def mode = environment.mode

  lazy val buildProductionUrlForPrototypedAPIs: Boolean = runModeConfiguration.getBoolean("buildProductionUrlForPrototypedAPIs").getOrElse(false)
  lazy val isSandbox: Boolean = runModeConfiguration.getBoolean("isSandbox").getOrElse(false)

  lazy val fetchByContextTtlInSeconds: String = runModeConfiguration.underlying.as[String]("fetchByContextTtlInSeconds")

//  lazy val publishApiDefinition = runModeConfiguration.getBoolean("publishApiDefinition").getOrElse(false)
//  lazy val apiContext = runModeConfiguration.getString("api.context").getOrElse("api-documentation")
//  lazy val access = runModeConfiguration.getConfig(s"api.access")
//  lazy val apiPlatformBearerToken = runModeConfiguration.getString(s"$env.api-platform.bearer-token")
//  lazy val requiresAuthorization = runModeConfiguration.getBoolean("requiresAuthorization").getOrElse(false)
//  lazy val topLevelRamlFile = "application.raml"

  override def baseUrl(serviceName: String) = {
    val context = getConfString(s"$serviceName.context", "")

    if (context.length > 0) s"${super.baseUrl(serviceName)}/$context"
    else super.baseUrl(serviceName)
  }
}
