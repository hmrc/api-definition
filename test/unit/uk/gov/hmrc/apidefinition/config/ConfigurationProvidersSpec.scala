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

package unit.uk.gov.hmrc.apidefinition.config

import java.util

import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment}
import uk.gov.hmrc.apidefinition.config.NotificationServiceConfigProvider
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import uk.gov.hmrc.apidefinition.services.{EmailNotificationService, LoggingNotificationService, NotificationService}
import org.mockito.ArgumentMatchers._

import scala.collection.JavaConverters._

class ConfigurationProvidersSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    val mockRunModeConfiguration: Configuration = mock[Configuration]
    val mockEnvironment: Environment = mock[Environment]
  }

  trait NotificationServiceConfigProviderSetup extends Setup {
    def notificationsConfigReturns(notificationType: String, emailAddresses: List[String] = List.empty): Unit = {
      val notificationsConfiguration: Configuration = mock[Configuration]

      when(notificationsConfiguration.getString(matches("type"), any[Option[Set[String]]])).thenReturn(Some(notificationType))
      when(notificationsConfiguration.getStringList("emailAddresses")).thenReturn(Some(new util.ArrayList(emailAddresses.asJavaCollection)))

      when(mockRunModeConfiguration.getConfig("notifications")).thenReturn(Some(notificationsConfiguration))
    }

    val underTest = new NotificationServiceConfigProvider(mockRunModeConfiguration, mockEnvironment)
  }

  "NotificationServiceConfigProvider" should {
    "return a LoggingNotificationService when type is specified as LOG" in new NotificationServiceConfigProviderSetup  {
      notificationsConfigReturns("LOG", List.empty)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
    }

    "return an EmailNotificationService when type is specified as EMAIL" in new NotificationServiceConfigProviderSetup  {
      private val emailAddresses = List("foo@bar.com", "bar@baz.com")
      notificationsConfigReturns("EMAIL", emailAddresses)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[EmailNotificationService]
      returnedNotificationService.asInstanceOf[EmailNotificationService].emailAddresses.toSeq shouldEqual emailAddresses
    }

    "default to LoggingNotificationService if no type specified" in new NotificationServiceConfigProviderSetup {
      notificationsConfigReturns("FOO", List.empty)

      val returnedNotificationService: NotificationService = underTest.get()

      returnedNotificationService shouldBe a[LoggingNotificationService]
    }
  }
}
