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


import java.util.concurrent.TimeUnit.{HOURS, SECONDS}

import javax.inject.{Inject, Provider, Singleton}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Mode.Mode
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.apidefinition.scheduled.{DeleteApisJobConfig, JobConfig}
import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, LoggingNotificationService, NotificationService}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class ConfigurationModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[DeleteApisJobConfig].toProvider[DeleteApisJobConfigProvider],
      bind[NotificationService].toProvider[NotificationServiceConfigProvider]
    )
  }
}

@Singleton
class DeleteApisJobConfigProvider @Inject()(val runModeConfiguration: Configuration, environment: Environment)
  extends Provider[DeleteApisJobConfig] with ServicesConfig {

  override protected def mode: Mode = environment.mode

  override def get(): DeleteApisJobConfig = {
    val jobConfig = runModeConfiguration.underlying.as[Option[JobConfig]]("deleteApisJob")
      .getOrElse(JobConfig(FiniteDuration(60, SECONDS), FiniteDuration(24, HOURS), enabled = true)) // scalastyle:off magic.number
    DeleteApisJobConfig(jobConfig.initialDelay, jobConfig.interval, jobConfig.enabled)
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
