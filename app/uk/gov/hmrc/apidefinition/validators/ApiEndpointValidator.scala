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

package uk.gov.hmrc.apidefinition.validators

import scala.util.matching.Regex


import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import cats.implicits._

object ApiEndpointValidator extends Validator[Endpoint] {
  private val uriRegex: Regex           = """^/[.]?[a-zA-Z0-9_\-\/{}]*$""".r
  private val pathParameterRegex: Regex = """^\{[a-zA-Z]+[a-zA-Z0-9_\-]*\}$""".r

  def validate(endpoint: Endpoint): HMRCValidatedNel[Endpoint] = {
    (
      validateEndpointName(endpoint.endpointName),
      validateUriPattern(endpoint.uriPattern),
      if(endpoint.authType == AuthType.USER) validateScope(endpoint.scope) else endpoint.scope.validNel,
      validatePathParameters(endpoint.uriPattern),
      validateQueryParameters(endpoint.queryParameters),
      validateUniqueParameterNames(endpoint)
    )
    .mapN { case _ => endpoint }
    .leftMap(_.map(s => s"${endpoint.endpointName} - $s"))
  }

  protected def validateEndpointName(endpointName: String): HMRCValidatedNel[String] = {
    endpointName.valid.ensure("Field 'endpoints.endpointName' is required")(_.nonEmpty).toValidatedNel
  }
  
  protected def validateUriPattern(uriPattern: String): HMRCValidatedNel[String] = {
    uriPattern.valid
    .ensure(s"Field 'endpoints.uriPattern' is required")(_.nonEmpty)
    .ensure(s"Field 'endpoints.uriPattern' with value '$uriPattern' should match regular expression '$uriRegex'")(y => y.matches(uriRegex))
    .toValidatedNel
  }

  protected def validateScope(scope: Option[String]): HMRCValidatedNel[Option[String]] = {
    scope.valid.ensure(s"Field 'endpoints.scope' is required")(_.filterNot(_.isBlank()).isDefined)
    .toValidatedNel
  }

  protected def validateQueryParameters(queryParameters: List[QueryParameter]): HMRCValidatedNel[List[QueryParameter]] = {
    queryParameters
      .map(qp => QueryParameterValidator.validate(qp).map(_ :: Nil))
      .combineAll
  }

  protected def validatePathParameters(uriPattern: String): HMRCValidatedNel[String] = {
    val isPathParam: String => Boolean = { segment: String => segment.contains("{") || segment.contains("}") }
    val segments                       = uriPattern.split("/").toList

    val pathParamValidationError: String => String = s => s"Curly-bracketed segment '$s' should match regular expression '$pathParameterRegex'"

    val validatePathParam: (String) => HMRCValidatedNel[String] = 
      _.valid.ensureOr(pathParamValidationError)(_.matches(pathParameterRegex)).toValidatedNel
      
    segments.filter(isPathParam)
      .map(pathParam => validatePathParam(pathParam).map(_ :: Nil))
      .combineAll
      .map(_ => uriPattern)
  }

  protected def validateUniqueParameterNames(endpoint: Endpoint): HMRCValidatedNel[Endpoint] = {
    def isVariable(segment: String): Boolean = segment.startsWith("{") && segment.endsWith("}")

    val pathParameters          = endpoint.uriPattern.split("/").filter(_.nonEmpty).toList.filter(isVariable)
    val queryParameters         = endpoint.queryParameters.map(_.name).map(x => s"{$x}")
    val duplicateParameterNames = pathParameters.intersect(queryParameters)

    endpoint.valid
      .ensure(s"Duplicate name for path and query parameters: ${duplicateParameterNames.mkString(",")}")(_ => duplicateParameterNames.isEmpty)
      .toValidatedNel
  }
}
