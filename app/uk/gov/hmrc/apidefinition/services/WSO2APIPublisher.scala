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

package uk.gov.hmrc.apidefinition.services

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.WSO2APIPublisherConnector
import uk.gov.hmrc.apidefinition.models.WSO2APIDefinition._
import uk.gov.hmrc.apidefinition.models.{APIDefinition, PublishingException, WSO2APIDefinition}
import uk.gov.hmrc.apidefinition.utils.WSO2PayloadHelper.buildWSO2APIDefinitions
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WSO2APIPublisher @Inject()(val appContext: AppConfig,
                                 val wso2PublisherConnector: WSO2APIPublisherConnector)
                                (implicit val ec: ExecutionContext) {

  val IgnoredContexts: Seq[String] = Seq("sso-in/sso", "web-session/sso-api")

  private def publish(apiDefinition: APIDefinition, cookie: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    Logger.info(s"Trying to publish API [${apiDefinition.name}]")

    if (IgnoredContexts.contains(apiDefinition.context)) {
      Logger.info(s"Ignoring publishing of ${apiDefinition.context}")
      Future.successful(())
    } else {
      def createOrUpdateAPI(wso2APIDefinition: WSO2APIDefinition): Future[Unit] = {
        wso2PublisherConnector.doesAPIExist(cookie, wso2APIDefinition).flatMap {
          case true => wso2PublisherConnector.updateAPI(cookie, wso2APIDefinition)
          case false => wso2PublisherConnector.createAPI(cookie, wso2APIDefinition)
        }
      }

      def publishApiStatuses(): Seq[Future[Unit]] = {
        buildWSO2APIDefinitions(apiDefinition).map {
          wso2ApiDefinition: WSO2APIDefinition => createOrUpdateAPI(wso2ApiDefinition).flatMap { _ =>
            val wso2APIStatus = wso2ApiStatus(apiDefinition, wso2ApiDefinition)
            wso2PublisherConnector.publishAPIStatus(cookie, wso2ApiDefinition, wso2APIStatus)
          }
        }
      }

      Future.sequence(publishApiStatuses()) map (_ => ()) recover {
        case e: Throwable =>
          Logger.error(s"Failed to publish API [${apiDefinition.name}]", e)
          throw PublishingException(apiDefinition.name)
      }
    }
  }

  def publish(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    wso2PublisherConnector.login().flatMap { cookie: String =>
      publish(apiDefinition, cookie)
    }
  }

  def publish(definitions: Seq[APIDefinition])(implicit hc: HeaderCarrier): Future[Seq[String]] = {

    def failure(future: Future[_]): Future[Option[Throwable]] = {
      future.map(_ => None).recover {
        case e => Some(e)
      }
    }

    for {
      cookie <- wso2PublisherConnector.login()
      result <-
        definitions.foldLeft(Future.successful(Seq[Option[Throwable]]())) {
          (fs, d) => fs.flatMap(seq => failure(publish(d, cookie)).map(ot => seq.:+(ot)))
        }
    } yield result.filter(_.isDefined).map(_.get.getMessage)
  }

  def hasSubscribers(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Boolean] = {

    def fetchAPI(cookie: String, wso2Definition: WSO2APIDefinition) = {
      wso2PublisherConnector.fetchAPI(cookie, wso2Definition.name, wso2Definition.version)
    }

    for {
      cookie <- wso2PublisherConnector.login()
      apis <- Future.sequence(buildWSO2APIDefinitions(apiDefinition).map(fetchAPI(cookie, _)))
      hasSubscribers = apis.exists(_.subscribersCount > 0)
    } yield hasSubscribers
  }

  def delete(apiDefinition: APIDefinition)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (IgnoredContexts.contains(apiDefinition.context)) {
      Logger.info(s"Ignoring deletion of ${apiDefinition.context}")
      Future.successful(())
    } else {
      def removeAPI(cookie: String, wso2Definition: WSO2APIDefinition): Future[Unit] = {
        wso2PublisherConnector.removeAPI(cookie, wso2Definition.name, wso2Definition.version)
      }

      for {
        cookie <- wso2PublisherConnector.login()
        _ <- Future.sequence(buildWSO2APIDefinitions(apiDefinition).map(removeAPI(cookie, _)))
      } yield ()
    }
  }
}
