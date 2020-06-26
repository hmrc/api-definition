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


import java.util.concurrent.TimeUnit.{DAYS, SECONDS}

import javax.inject.{Inject, Provider, Singleton}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger, Mode}
import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, LoggingNotificationService, NotificationService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[NotificationService].toProvider[NotificationServiceConfigProvider]
    )
  }
}

@Singleton
class NotificationServiceConfigProvider @Inject()(val runModeConfiguration: Configuration,
                                                  environment: Environment,
                                                  httpClient: HttpClient,
                                                  servicesConfig: ServicesConfig) extends Provider[NotificationService]{

  private val LoggerNotificationType = "LOG"
  private val EmailNotificationType = "EMAIL"

  private val UnknownEnvironmentName: String = "Unknown"

  protected def mode: Mode = environment.mode

  override def get(): NotificationService = {
    runModeConfiguration.getOptional[Configuration]("notifications") match {
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
        (emailConfiguration.getOptional[String]("serviceURL"),
          emailConfiguration.getOptional[String]("templateId"),
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

    val environmentName = configuration.getOptional[String]("environmentName").getOrElse(UnknownEnvironmentName)
    configuration.getAndValidate("type", Set(LoggerNotificationType, EmailNotificationType)) match {
      case LoggerNotificationType =>
        Logger.info("Using Logging Notification Service")
        new LoggingNotificationService(environmentName)
      case EmailNotificationType =>
        Logger.info("Using Email Notification Service")
        configureEmailNotificationService(environmentName, configuration.getOptional[Configuration]("email"))
      case _ =>
        Logger.warn("Notification type not recognised")
        defaultNotificationService(environmentName)
    }
  }
}
