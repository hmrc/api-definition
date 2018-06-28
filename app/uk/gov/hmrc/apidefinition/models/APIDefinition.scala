/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.models

import org.joda.time.DateTime
import uk.gov.hmrc.apidefinition.models.APIStatus.APIStatus
import uk.gov.hmrc.apidefinition.models.AuthType.AuthType
import uk.gov.hmrc.apidefinition.models.HttpMethod.HttpMethod
import uk.gov.hmrc.apidefinition.models.ResourceThrottlingTier.ResourceThrottlingTier

case class APIDefinition(serviceName: String,
                         serviceBaseUrl: String,
                         name: String,
                         description: String,
                         context: String,
                         versions: Seq[APIVersion],
                         requiresTrust: Option[Boolean],
                         isTestSupport: Option[Boolean] = None,
                         lastPublishedAt: Option[DateTime] = None) {

  private def forbidEmptyString(item: String, errorMsg: String): Unit = {
    require(item.nonEmpty, errorMsg)
  }

  private def forbidCharSequenceToItem(item: String, nonAllowedCharSeq: String, errorMsg: String): Unit = {
    require(!item.contains(nonAllowedCharSeq), errorMsg)
  }

  private def uniqueVersions = {
    !versions.map(_.version).groupBy(identity).mapValues(_.size).exists(_._2 > 1)
  }

  private val forbiddenCharSequencesInEndpointUri = Seq("?", "&", ":")
  private val forbiddenCharSequencesInQueryParamName = Seq("?", "&", ":", "/", "{", "}")

  private val nonEmptyApiDefinitionFields = Seq("serviceName", "serviceBaseUrl", "name", "context", "description")

  getClass.getDeclaredFields foreach { f =>
    val fieldName = f.getName
    if (nonEmptyApiDefinitionFields.contains(fieldName)) {
      f.setAccessible(true)
      forbidEmptyString(f.get(this).asInstanceOf[String], s"$fieldName is required")
    }
  }

  require(versions.nonEmpty, "at least one version is required")
  require(uniqueVersions, "version numbers must be unique")

  versions.foreach { version: APIVersion =>

    forbidEmptyString(version.version, "version is required")
    require(version.endpoints.nonEmpty, "at least one endpoint is required")

    version.status match {
      case APIStatus.ALPHA | APIStatus.BETA | APIStatus.STABLE =>
        require(version.endpointsEnabled.nonEmpty, "endpointsEnabled is required if status is ALPHA, BETA or STABLE")
      case _ => ()
    }

    version.endpoints.foreach { endpoint: Endpoint =>

      def forbidCharSequenceToEndpointUri(charSeq: String): Unit = {
        forbidCharSequenceToItem(
          item = endpoint.uriPattern,
          nonAllowedCharSeq = charSeq,
          errorMsg = s"endpoint cannot contain $charSeq in the URI"
        )
      }

      for (charSeq <- forbiddenCharSequencesInEndpointUri) {
        forbidCharSequenceToEndpointUri(charSeq)
      }
      forbidEmptyString(endpoint.endpointName, "endpointName is required")

      endpoint.queryParameters.getOrElse(Nil).foreach { queryParam: Parameter =>

        def forbidCharSequenceToQueryParamName(charSeq: String): Unit = {
          forbidCharSequenceToItem(
            item = queryParam.name,
            nonAllowedCharSeq = charSeq,
            errorMsg = s"query parameter name cannot contain $charSeq in the name"
          )
        }

        forbidEmptyString(queryParam.name, "query parameter name cannot be empty")
        for (charSeq <- forbiddenCharSequencesInQueryParamName) {
          forbidCharSequenceToQueryParamName(charSeq)
        }
      }

      endpoint.authType match {
        case AuthType.USER => require(endpoint.scope.nonEmpty, "scope is required if authType is USER")
        case AuthType.APPLICATION => require(endpoint.scope.isEmpty, "scope is not required if authType is APPLICATION")
        case _ => ()
      }

    }

  }

}

case class ExtendedAPIDefinition(serviceName: String,
                                 serviceBaseUrl: String,
                                 name: String,
                                 description: String,
                                 context: String,
                                 requiresTrust: Boolean,
                                 isTestSupport: Boolean,
                                 versions: Seq[ExtendedAPIVersion],
                                 lastPublishedAt: Option[DateTime])

case class ExtendedAPIVersion(version: String,
                              status: APIStatus,
                              endpoints: Seq[Endpoint],
                              productionAvailability: Option[APIAvailability],
                              sandboxAvailability: Option[APIAvailability])

case class APIAvailability(endpointsEnabled: Boolean, access: APIAccess, loggedIn: Boolean, authorised: Boolean)

case class APIVersion(version: String,
                      status: APIStatus,
                      access: Option[APIAccess] = Some(PublicAPIAccess()),
                      endpoints: Seq[Endpoint],
                      endpointsEnabled: Option[Boolean] = None)

// API resource (also called API endpoint)
case class Endpoint(uriPattern: String,
                    endpointName: String,
                    method: HttpMethod,
                    authType: AuthType,
                    throttlingTier: ResourceThrottlingTier,
                    scope: Option[String] = None,
                    queryParameters: Option[Seq[Parameter]] = None)

// Query Parameter
case class Parameter(name: String, required: Boolean = false)

case class PublishingException(message: String) extends Exception(message)

case class ContextAlreadyDefinedForAnotherService(context: String, serviceName: String)
  extends RuntimeException(s"Context '$context' was already defined for service '$serviceName'")

object APIAccessType extends Enumeration {
  type APIAccessType = Value
  val PRIVATE, PUBLIC = Value
}

trait APIAccess

case class PublicAPIAccess() extends APIAccess

case class PrivateAPIAccess(whitelistedApplicationIds: Seq[String]) extends APIAccess

object APIStatus extends Enumeration {
  type APIStatus = Value
  val PROTOTYPED, PUBLISHED, ALPHA, BETA, STABLE, DEPRECATED, RETIRED = Value
}

object AuthType extends Enumeration {
  type AuthType = Value
  val NONE, APPLICATION, USER = Value
}

object HttpMethod extends Enumeration {
  type HttpMethod = Value
  val GET, POST, PUT, DELETE, OPTIONS = Value
}

object ResourceThrottlingTier extends Enumeration {
  type ResourceThrottlingTier = Value
  val UNLIMITED = Value
}

object SubscriptionThrottlingTier extends Enumeration {
  type ThrottlingTier = Value
  val BRONZE_SUBSCRIPTION, SILVER_SUBSCRIPTION, GOLD_SUBSCRIPTION, PLATINUM_SUBSCRIPTION = Value
}
