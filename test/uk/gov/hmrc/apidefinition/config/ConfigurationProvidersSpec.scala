/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util
import java.util.UUID

import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, LoggingNotificationService, NotificationService}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.collection.JavaConverters._

class ConfigurationProvidersSpec extends AsyncHmrcSpec with BeforeAndAfterEach {

  val mockRunModeConfiguration = mock[Configuration]
  val mockEnvironment = mock[Environment]

  trait Setup {

  }

  override def beforeEach(): Unit ={
    super.beforeEach()
    reset(mockEnvironment, mockRunModeConfiguration)
  }

  trait NotificationServiceConfigProviderSetup extends Setup {
    val environmentName: String = "External Test"

    def notificationConfigReturnsValidLoggingConfiguration(environmentName: String): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]

      when(notificationsConfiguration.getAndValidate[String](eqTo("type"),*)(*)).thenReturn("LOG")
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(Some(environmentName))

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    def notificationConfigReturnsUnknownNotificationType(): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]
      when(notificationsConfiguration.getAndValidate[String](eqTo("type"),*)(*)).thenReturn("FOO")
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(Some(environmentName))

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    def notificationsConfigReturnsValidEmailConfiguration(emailServiceURL:String,
                                                          emailTemplateId:String,
                                                          emailAddresses: List[String] = List.empty): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]
      val emailConfiguration: Configuration = mock[Configuration]

      when(notificationsConfiguration.getAndValidate[String](eqTo("type"),*)(*)).thenReturn("EMAIL")
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(Some(environmentName))
      when(notificationsConfiguration.getOptional[Configuration](eqTo("email"))(*)).thenReturn(Some(emailConfiguration))

      when(emailConfiguration.getOptional[String](eqTo("serviceURL"))(*)).thenReturn(Some(emailServiceURL))
      when(emailConfiguration.getOptional[String](eqTo("templateId"))(*)).thenReturn(Some(emailTemplateId))

      when(emailConfiguration.getStringList("addresses")).thenReturn(Some(new util.ArrayList(emailAddresses.asJavaCollection)))

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    def notificationsConfigReturnsMissingEmailConfiguration(): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]

      when(notificationsConfiguration.getAndValidate[String](eqTo("type"),*)(*)).thenReturn("EMAIL")
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(Some(environmentName))
      when(notificationsConfiguration.getOptional[Configuration](eqTo("email"))(*)).thenReturn(None)

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    def notificationsConfigReturnsInvalidEmailConfiguration(): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]
      val emailConfiguration: Configuration = mock[Configuration]

      when(notificationsConfiguration.getAndValidate[String](eqTo("type"),*)(*)).thenReturn("EMAIL")
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(Some(environmentName))
      when(notificationsConfiguration.getOptional[Configuration](eqTo("email"))(*)).thenReturn(Some(emailConfiguration))

      when(emailConfiguration.getOptional[String](eqTo("serviceURL"))(*)).thenReturn(None)
      when(emailConfiguration.getOptional[String](eqTo("templateId"))(*)).thenReturn(None)
      when(emailConfiguration.getStringList("addresses")).thenReturn(None)

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    def loggingNotificationsConfigReturnsMissingEnvironmentName(): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]
      when(notificationsConfiguration.getOptional[String](eqTo("type"))(*)).thenReturn(Some("LOG"))
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(None)

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    def emailNotificationsConfigReturnsMissingEnvironmentName(emailServiceURL:String,
                                                              emailTemplateId:String,
                                                              emailAddresses: List[String] = List.empty): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]
      val emailConfiguration: Configuration = mock[Configuration]

      when(notificationsConfiguration.getAndValidate[String](eqTo("type"),*)(*)).thenReturn("EMAIL")
      when(notificationsConfiguration.getOptional[String](eqTo("environmentName"))(*)).thenReturn(None)
      when(notificationsConfiguration.getOptional[Configuration](eqTo("email"))(*)).thenReturn(Some(emailConfiguration))

      when(emailConfiguration.getOptional[String](eqTo("serviceURL"))(*)).thenReturn(Some(emailServiceURL))
      when(emailConfiguration.getOptional[String](eqTo("templateId"))(*)).thenReturn(Some(emailTemplateId))
      when(emailConfiguration.getStringList("addresses")).thenReturn(Some(new util.ArrayList(emailAddresses.asJavaCollection)))

      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(Some(notificationsConfiguration))
    }

    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockServiceConfig: ServicesConfig = mock[ServicesConfig]
    val underTest = new NotificationServiceConfigProvider(mockRunModeConfiguration, mockEnvironment, mockHttpClient, mockServiceConfig)
  }

  "NotificationServiceConfigProvider" should {
    val emailServiceURL: String = "https://localhost:9876/hmrc/email"
    val emailTemplateId: String = UUID.randomUUID().toString

    "return a LoggingNotificationService when type is specified as LOG" in new NotificationServiceConfigProviderSetup  {
      notificationConfigReturnsValidLoggingConfiguration(environmentName)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
      returnedNotificationService.asInstanceOf[LoggingNotificationService].environmentName shouldBe environmentName
    }

    "return an EmailNotificationService when type is specified as EMAIL" in new NotificationServiceConfigProviderSetup  {
      private val emailAddresses = List("foo@bar.com", "bar@baz.com")
      notificationsConfigReturnsValidEmailConfiguration(emailServiceURL, emailTemplateId, emailAddresses)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[EmailNotificationService]
      returnedNotificationService.asInstanceOf[EmailNotificationService].environmentName shouldBe environmentName
      returnedNotificationService.asInstanceOf[EmailNotificationService].emailAddresses.toSeq shouldEqual emailAddresses
    }

    "default to LoggingNotificationService if email configuration is incorrect" in new NotificationServiceConfigProviderSetup {
      notificationsConfigReturnsInvalidEmailConfiguration()

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
    }

    "default to LoggingNotificationService if email configuration is missing" in new NotificationServiceConfigProviderSetup {
      notificationsConfigReturnsMissingEmailConfiguration()

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
    }

    "default to LoggingNotificationService if invalid type specified" in new NotificationServiceConfigProviderSetup {
      notificationConfigReturnsUnknownNotificationType()

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
    }


    "default to LoggingNotificationService if not configuration specified" in new NotificationServiceConfigProviderSetup {
      when(mockRunModeConfiguration.getOptional[Configuration](eqTo("notifications"))(*)).thenReturn(None)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
    }

    "use the default environment name for Logging Notifications when it is not specified" in new NotificationServiceConfigProviderSetup {
      loggingNotificationsConfigReturnsMissingEnvironmentName()

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
      returnedNotificationService.asInstanceOf[LoggingNotificationService].environmentName shouldBe "Unknown"
    }

    "use the default environment name for Email Notifications when it is not specified" in new NotificationServiceConfigProviderSetup {
      private val emailAddresses = List("foo@bar.com", "bar@baz.com")
      emailNotificationsConfigReturnsMissingEnvironmentName(emailServiceURL, emailTemplateId, emailAddresses)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[EmailNotificationService]
      returnedNotificationService.asInstanceOf[EmailNotificationService].environmentName shouldBe "Unknown"
      returnedNotificationService.asInstanceOf[EmailNotificationService].emailAddresses.toSeq shouldEqual emailAddresses
    }
  }
}
