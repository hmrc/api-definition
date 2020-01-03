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

package uk.gov.hmrc.apidefinition.config


import javax.inject.{Inject, Provider, Singleton}
import play.api.Mode.Mode
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.apidefinition.controllers.{ApiVersionConfig, DocumentationConfig}
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, LoggingNotificationService, NotificationService}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.collection.JavaConverters._
import scala.util.Try

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[DocumentationConfig].toProvider[DocumentationConfigProvider],
      bind[NotificationService].toProvider[NotificationServiceConfigProvider]
    )
  }
}

@Singleton
class DocumentationConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[DocumentationConfig] with ServicesConfig {

  override protected def mode = environment.mode

  override def get() = {
    val versionRegex = "\\d+\\.\\d+".r

    def versionConfiguration(version: String) = {
      val accessType = runModeConfiguration.getString(s"api.$version.access.type")
        .flatMap(name => Try(APIAccessType.withName(name)).toOption)
        .getOrElse(APIAccessType.PUBLIC)

      val status = runModeConfiguration.getString(s"api.$version.status")
        .flatMap(name => Try(APIStatus.withName(name)).toOption)
        .getOrElse(APIStatus.ALPHA)

      val whitelistedApplicationIds = runModeConfiguration.getStringSeq(s"api.$version.access.whitelistedApplicationIds").getOrElse(Seq.empty)

      val isTrial = runModeConfiguration.getBoolean(s"api.$version.access.isTrial")

      val endpointsEnabled = runModeConfiguration.getBoolean(s"api.$version.endpointsEnabled").getOrElse(true)

      val access = accessType match {
        case APIAccessType.PUBLIC => PublicAPIAccess()
        case APIAccessType.PRIVATE => PrivateAPIAccess(whitelistedApplicationIds, isTrial)
      }

      ApiVersionConfig(version, status, access, endpointsEnabled)
    }

    val apiVersionConfigurations = (for {
      api <- runModeConfiguration.getConfig("api")
      versions = api.keys.flatMap(key => versionRegex.findFirstIn(key).toSet).toSeq.sorted
    } yield {
      versions.map(versionConfiguration)
    }).getOrElse(Seq.empty)

    val publishApiDefinition = runModeConfiguration.getBoolean("publishApiDefinition").getOrElse(false)

    DocumentationConfig(publishApiDefinition, apiVersionConfigurations)
  }
}

@Singleton
class NotificationServiceConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment, httpClient: HttpClient)
  extends Provider[NotificationService] with ServicesConfig {

  private val LoggerNotificationType = "LOG"
  private val EmailNotificationType = "EMAIL"

  private val UnknownEnvironmentName: String = "Unknown"

  override protected def mode: Mode = environment.mode

  override def get(): NotificationService = {
    runModeConfiguration.getConfig("notifications") match {
      case Some(notificationsConfig) => configureNotificationsService(notificationsConfig)
      case None => new LoggingNotificationService(UnknownEnvironmentName)
    }
  }

  private def configureNotificationsService(configuration: Configuration): NotificationService = {
    def defaultNotificationService(environmentName: String): NotificationService = {
      Logger.warn("Using Default Notification Service")
      new LoggingNotificationService(environmentName)
    }

    def configureEmailNotificationService(environmentName: String, optionalEmailConfiguration: Option[Configuration]): NotificationService = {
      if(optionalEmailConfiguration.isEmpty) {
        Logger.warn(s"No Email configuration provided")
        defaultNotificationService(environmentName)
      } else {
        val emailConfiguration = optionalEmailConfiguration.get
        (emailConfiguration.getString("serviceURL"),
          emailConfiguration.getString("templateId"),
          emailConfiguration.getStringList("addresses")) match {

          case (Some(emailServiceURL), Some(emailTemplateId), Some(emailAddresses)) =>
            Logger.info(s"Email notifications will be sent to $emailAddresses using template [$emailTemplateId] for [$environmentName] environment")
            new EmailNotificationService(httpClient, emailServiceURL, emailTemplateId, environmentName, emailAddresses.asScala.toSet)
          case _ =>
            Logger.warn(s"Failed to create EmailNotificationService")
            defaultNotificationService(environmentName)
        }
      }
    }

    val environmentName = configuration.getString("environmentName").getOrElse(UnknownEnvironmentName)
    configuration.getString("type", Some(Set(LoggerNotificationType, EmailNotificationType))) match {
      case Some(LoggerNotificationType) =>
        Logger.info("Using Logging Notification Service")
        new LoggingNotificationService(environmentName)
      case Some(EmailNotificationType) =>
        Logger.info("Using Email Notification Service")
        configureEmailNotificationService(environmentName, configuration.getConfig("email"))
      case _ =>
        Logger.warn("Notification type not recognised")
        defaultNotificationService(environmentName)
    }

  }
}
