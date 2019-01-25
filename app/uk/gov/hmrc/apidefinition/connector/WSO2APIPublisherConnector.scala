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

package uk.gov.hmrc.apidefinition.connector

import com.google.common.base.Charsets
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.ContentTypes.FORM
import play.api.http.HeaderNames._
import play.api.libs.json._
import play.utils.UriEncoding
import uk.gov.hmrc.apidefinition.config.WSHttp
import uk.gov.hmrc.apidefinition.models.JsonFormatters._
import uk.gov.hmrc.apidefinition.models.{WSO2APIDefinition, WSO2EndpointConfig}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class WSO2APIPublisherConnector @Inject()(servicesConfig: ServicesConfig, http: WSHttp) {

  val username: String = servicesConfig.getConfString("wso2-publisher.username", "admin")
  val password: String = servicesConfig.getConfString("wso2-publisher.password", "admin")
  val serviceUrl: String = s"${servicesConfig.baseUrl("wso2-publisher")}/publisher/site/blocks"

  def login()(implicit hc: HeaderCarrier): Future[String] = {
    val url = s"$serviceUrl/user/login/ajax/login.jag"
    val encodedPassword = UriEncoding.encodePathSegment(password, Charsets.UTF_8.name())
    val payload = s"action=login&username=$username&password=$encodedPassword"

    post(url, payload, contentTypeHeader(), hideCredentials = true).map { response =>
      val cookie = response.allHeaders(SET_COOKIE) mkString ";"
      Logger.debug("Login cookie:" + cookie)
      cookie
    }
  }

  def fetchAPI(cookie: String, wso2APIName: String, version: String)
              (implicit hc: HeaderCarrier): Future[WSO2APIDefinition] = {

    val url = s"$serviceUrl/listing/ajax/item-list.jag"

    val payload = s"action=getAPI&name=$wso2APIName&version=$version&provider=$username"

    post(url, payload, addCookieHeader(cookie)).map { response =>
      val json = response.json
      WSO2APIDefinition(
        name = (json \ "api" \ "name").as[String],
        context = (json \ "api" \ "context").as[String],
        version = (json \ "api" \ "version").as[String],
        subscribersCount = (json \ "api" \ "subs").as[Int],
        endpointConfig = Json.parse((json \ "api" \ "endpointConfig").as[String]).as[WSO2EndpointConfig],
        swagger = None // WSO2 Publisher returns the API endpoints details
                       // under `(json \ "resources")` and under `(json \ "templates")`,
                       // but since it is not needed here for the `fetchAPI` method,
                       // we decided to ignore parsing these extra fields from the WSO2 Publisher JSON response
      )
    }
  }

  def doesAPIExist(cookie: String, wso2APIDefinition: WSO2APIDefinition)
                  (implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$serviceUrl/item-add/ajax/add.jag"
    val payload = s"action=isAPINameExist&apiName=${wso2APIDefinition.name}"

    post(url, payload, addCookieHeader(cookie)).map { response =>
      (response.json \ "exist").as[String].toBoolean
    }
  }

  def createAPI(cookie: String, wso2APIDefinition: WSO2APIDefinition)
               (implicit hc: HeaderCarrier): Future[Unit] = {
    writeAPI(action = "addAPI", wso2APIDefinition, cookie)
  }

  def updateAPI(cookie: String, wso2APIDefinition: WSO2APIDefinition)
               (implicit hc: HeaderCarrier): Future[Unit] = {
    writeAPI(action = "updateAPI", wso2APIDefinition, cookie)
  }

  def publishAPIStatus(cookie: String, wso2APIDefinition: WSO2APIDefinition, status: String)
                      (implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"$serviceUrl/life-cycles/ajax/life-cycles.jag"
    val payload = s"action=updateStatus&name=${wso2APIDefinition.name}&version=${wso2APIDefinition.version}&" +
      s"provider=$username&status=$status&publishToGateway=true&requireResubscription=false"

    post(url, payload, addCookieHeader(cookie)).map { _ => () }
  }

  def removeAPI(cookie: String, wso2APIName: String, version: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"$serviceUrl/item-add/ajax/remove.jag"
    val payload = s"action=removeAPI&name=$wso2APIName&version=$version&provider=$username"

    post(url, payload, addCookieHeader(cookie)).map { _ => () }
  }

  private def writeAPI(action: String, wso2APIDefinition: WSO2APIDefinition, cookie: String)
                      (implicit hc: HeaderCarrier): Future[Unit] = {

    val url = s"$serviceUrl/item-add/ajax/add.jag"

    val payload = s"""action=$action
                     |&name=${wso2APIDefinition.name}
                     |&visibility=public
                     |&version=${wso2APIDefinition.version}
                     |&description=
                     |&provider=$username
                     |&endpointType=nonsecured
                     |&http_checked=
                     |&https_checked=https
                     |&tags=
                     |&thumbUrl=
                     |&context=${wso2APIDefinition.context}
                     |&tiersCollection=${WSO2APIDefinition.allAvailableWSO2SubscriptionTiers.mkString(",")}
                     |&endpoint_config=${Json.toJson(wso2APIDefinition.endpointConfig)}
                     |&swagger=${Json.toJson(wso2APIDefinition.swagger)}
                     |""".stripMargin.replaceAll("\n", "")

    post(url, payload, addCookieHeader(cookie)).map { _ => () }
  }

  private def post[R](url: String, payload: String, headers: Seq[(String, String)], hideCredentials: Boolean = false)
                     (implicit hc: HeaderCarrier): Future[HttpResponse] = {

    def payloadWithHiddenCredentials = {
      val firstOccurrenceIndex = payload.indexOf("username")
      payload.substring(0, firstOccurrenceIndex) + "username=*****&password=*****"
    }

    if (hideCredentials) {
      Logger.info(s"Posting payload [$payloadWithHiddenCredentials] to url [$url] with headers [$headers]")
    } else {
      Logger.info(s"Posting payload [$payload] to url [$url] with headers [$headers]")
    }

    http.POSTString(url, payload, headers).map { handleResponse(url) }
  }

  private def handleResponse[R](url: String)(response: HttpResponse): HttpResponse = {
    Try((response.json \ "error").as[Boolean]) match {
      case Success(false) => response
      case Success(true) =>
        Logger.warn(s"Error found after calling $url: ${response.body}")
        throw new RuntimeException((response.json \ "message").as[String])
      case Failure(_) =>
        Logger.warn(s"Error found after calling $url: ${response.body}")
        throw new RuntimeException(s"${response.body}")
    }
  }

  private def contentTypeHeader(): Seq[(String, String)] = {
    Seq(CONTENT_TYPE -> FORM)
  }

  private def addCookieHeader(cookie: String): Seq[(String, String)] = {
    contentTypeHeader :+ (COOKIE -> cookie)
  }

}
