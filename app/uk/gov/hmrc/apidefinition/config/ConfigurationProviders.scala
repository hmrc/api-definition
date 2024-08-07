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

import javax.inject.{Inject, Provider, Singleton}

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.ramltools.loaders.{RamlLoader, UrlRewriter}

import uk.gov.hmrc.apidefinition.raml.{DocumentationRamlLoader, DocumentationUrlRewriter}
import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, LoggingNotificationService, NotificationService}
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[NotificationService].toProvider[NotificationServiceConfigProvider],
      bind[RamlLoader].to[DocumentationRamlLoader],
      bind[UrlRewriter].to[DocumentationUrlRewriter]
    )
  }
}

@Singleton
class NotificationServiceConfigProvider @Inject() (val runModeConfiguration: Configuration, environment: Environment, httpClient: HttpClientV2, servicesConfig: ServicesConfig)
    extends Provider[NotificationService] with ApplicationLogger {

  private val LoggerNotificationType = "LOG"
  private val EmailNotificationType  = "EMAIL"

  private val UnknownEnvironmentName: String = "Unknown"

  protected def mode: Mode = environment.mode

  override def get(): NotificationService = {
    runModeConfiguration.getOptional[Configuration]("notifications") match {
      case Some(notificationsConfig) => configureNotificationsService(notificationsConfig)
      case None                      => new LoggingNotificationService(UnknownEnvironmentName)
    }
  }

  private def configureNotificationsService(configuration: Configuration): NotificationService = {
    def defaultNotificationService(environmentName: String): NotificationService = {
      logger.warn("Using Default Notification Service")
      new LoggingNotificationService(environmentName)
    }

    def configureEmailNotificationService(environmentName: String, optionalEmailConfiguration: Option[Configuration]): NotificationService = {
      if (optionalEmailConfiguration.isEmpty) {
        logger.warn(s"No Email configuration provided")
        defaultNotificationService(environmentName)
      } else {
        val emailConfiguration = optionalEmailConfiguration.get
        (emailConfiguration.getOptional[String]("serviceURL"), emailConfiguration.getOptional[String]("templateId"), emailConfiguration.get[Seq[String]]("addresses")) match {

          case (Some(emailServiceURL), Some(emailTemplateId), emailAddresses) =>
            logger.info(s"Email notifications will be sent to $emailAddresses using template [$emailTemplateId] for [$environmentName] environment")
            new EmailNotificationService(httpClient, url"$emailServiceURL", emailTemplateId, environmentName, emailAddresses.toSet)
          case _                                                              =>
            logger.warn(s"Failed to create EmailNotificationService")
            defaultNotificationService(environmentName)
        }
      }
    }

    val environmentName = configuration.getOptional[String]("environmentName").getOrElse(UnknownEnvironmentName)
    configuration.getAndValidate("type", Set(LoggerNotificationType, EmailNotificationType)) match {
      case LoggerNotificationType =>
        logger.info("Using Logging Notification Service")
        new LoggingNotificationService(environmentName)
      case EmailNotificationType  =>
        logger.info("Using Email Notification Service")
        configureEmailNotificationService(environmentName, configuration.getOptional[Configuration]("email"))
      case _                      =>
        logger.warn("Notification type not recognised")
        defaultNotificationService(environmentName)
    }
  }
}
