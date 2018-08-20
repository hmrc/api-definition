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

import scala.util.matching.Regex

object APIDefinitionValidator {

  private val queryParamRegex: Regex = "^[a-zA-Z0-9_\\-]+$".r
  private val contextRegex: Regex = "^[a-zA-Z0-9_\\-\\/]+$".r
  private val uriRegex: Regex = "^[a-zA-Z0-9_\\-\\/{}]+$".r

  private val nonEmptyApiDefinitionFields = Seq("name", "serviceName", "serviceBaseUrl", "context", "description")

  def validate(definition: APIDefinition): Unit = {
    validateNonEmptyFields(definition)
    validateContext(definition.context)
    require(uniqueVersions(definition), s"version numbers must be unique for API '${definition.name}'")
    definition.versions.foreach(validateVersion(definition.name))
  }

  private def validateContext(apiName: String): String => Unit = { context: String =>
    lazy val errMsg = s"invalid context for API '$apiName': $context"

    require(!context.startsWith("/"), errMsg)

    require(!context.endsWith("/"), errMsg)

    require(!context.contains("//"), errMsg)

    require(hasMatch(contextRegex, context), errMsg)
  }

  private def validateVersion(apiName: String): APIVersion => Unit = { version: APIVersion =>
    forbidEmptyString(version.version, s"field 'version' is required for API '$apiName'")

    require(version.endpoints.nonEmpty, s"at least one endpoint is required for API '$apiName' version '${version.version}'")

    validateStatus(apiName, version)

    version.endpoints.foreach(validateEndpoint(apiName, version.version))
  }

  private def validateStatus(apiName: String, version: APIVersion): Unit = {
    version.status match {
      case APIStatus.ALPHA | APIStatus.BETA | APIStatus.STABLE =>
        require(version.endpointsEnabled.nonEmpty, s"endpointsEnabled is required for API '$apiName' version '${version.version}'")
      case _ => ()
    }
  }

  private def validateEndpoint(apiName: String, version: String): Endpoint => Unit = { endpoint: Endpoint =>
    validateUriPattern(apiName, version, endpoint.endpointName, endpoint.uriPattern)

    endpoint.queryParameters.getOrElse(Nil).foreach(validateQueryParameter(apiName, version, endpoint.endpointName))

    validateAuthType(apiName, version, endpoint)
  }

  private def validateUriPattern(apiName: String, version: String, endpointName: String, uriPattern: String): Unit = {
    require(uriPattern.nonEmpty, s"URI pattern is required for endpoint '$endpointName' in the API '$apiName' version '$version'")

    // TODO: extra validation for URI patterns
    // create separate method
    // add unit tests
    // number of '{'s matches the number of '}'s
    // URI pattern does not contain "{}" (empty path parameter)
    // there are no nested curly brackets (path parameters of path parameters are not allowed)
    // at anytime in the string: #('}'s) == #('}'s) || #('}'s) == 1 + #('}'s)
    // check look ahead - first look operators in the regex

    require(hasMatch(uriRegex, uriPattern),
      s"invalid URI pattern for endpoint '$endpointName' in the API '$apiName' version '$version': $uriPattern")
  }

  private def validateAuthType(apiName: String, version: String, endpoint: Endpoint): Unit = {
    endpoint.authType match {
      case AuthType.USER => require(endpoint.scope.nonEmpty,
        s"scope is required for endpoint '${endpoint.endpointName}' in the API '$apiName' version '$version'")
      case AuthType.APPLICATION => require(endpoint.scope.isEmpty,
        s"scope is not allowed for endpoint '${endpoint.endpointName}' in the API '$apiName' version '$version'")
      case _ => ()
    }
  }

  private def validateQueryParameter(apiName: String, version: String, endpointName: String): Parameter => Unit = { queryParam: Parameter =>
    require(queryParam.name.nonEmpty, s"query parameter name is required for endpoint '$endpointName' in the API '$apiName' version '$version'")
    require(hasMatch(queryParamRegex, queryParam.name),
      s"invalid query parameter name for endpoint '$endpointName' in the API '$apiName' version '$version': ${queryParam.name}")
  }

  private def validateNonEmptyFields(definition: APIDefinition): Unit = {

    def errorMessageSuffix: String => String = { fieldName =>
      if (fieldName == "name") "" else s" for API '${definition.name}'"
    }

    require(definition.versions.nonEmpty, s"at least one version is required for API '${definition.name}'")
    definition.getClass.getDeclaredFields foreach { f =>
      val fieldName = f.getName
      if (nonEmptyApiDefinitionFields.contains(fieldName)) {
        f.setAccessible(true)
        lazy val suffix = errorMessageSuffix(fieldName)
        forbidEmptyString(f.get(definition).asInstanceOf[String], s"field '$fieldName' is required$suffix")
      }
    }
  }

  private def forbidEmptyString(item: String, errorMsg: String): Unit = {
    require(item.nonEmpty, errorMsg)
  }

  private def uniqueVersions(definition: APIDefinition): Boolean = {
    !definition.versions.map(_.version).groupBy(identity).mapValues(_.size).exists(_._2 > 1)
  }

  private def hasMatch: (Regex, String) => Boolean = {
    (r: Regex, v: String) => r.findFirstIn(v).nonEmpty
  }

}
