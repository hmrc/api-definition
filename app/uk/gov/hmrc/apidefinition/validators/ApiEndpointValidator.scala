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

import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidefinition.models.{AuthType, Endpoint, Parameter}

import scala.concurrent.ExecutionContext
import scala.util.matching.Regex

@Singleton
class ApiEndpointValidator @Inject() (queryParameterValidator: QueryParameterValidator)(implicit override val ec: ExecutionContext) extends Validator[Endpoint] {

  private val uriRegex: Regex           = "^/[a-zA-Z0-9_\\-\\/{}]*$".r
  private val pathParameterRegex: Regex = "^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$".r

  def validate(ec: String)(implicit endpoint: Endpoint): HMRCValidated[Endpoint] = {
    val errorContext: String = if (endpoint.endpointName.isEmpty) ec else s"$ec endpoint '${endpoint.endpointName}'"
    (
      validateThat(_.endpointName.nonEmpty, _ => s"Field 'endpoints.endpointName' is required $errorContext"),
      validateUriPattern(errorContext),
      validateScope(errorContext),
      validatePathParameters(errorContext, endpoint),
      validateQueryParameters(errorContext, endpoint)
    ).mapN((_, _, _, _, _) => endpoint)
  }

  private def validateUriPattern(errorContext: String)(implicit endpoint: Endpoint): HMRCValidated[Endpoint] = {
    validateThat(_.uriPattern.nonEmpty, _ => s"Field 'endpoints.uriPattern' is required $errorContext").andThen(
      validateField(
        _.uriPattern.matches(uriRegex),
        u => s"Field 'endpoints.uriPattern' with value '${u.uriPattern}' should match regular expression '$uriRegex' $errorContext"
      )
    )
  }

  private def validateScope(errorContext: String)(implicit endpoint: Endpoint): HMRCValidated[Endpoint] = {
    endpoint.authType match {
      case AuthType.USER => validateThat(_ => endpoint.scope.nonEmpty, _ => s"Field 'endpoints.scope' is required $errorContext")
      case _             => endpoint.validNel
    }
  }

  private def validatePathParameters(errorContext: String, endpoint: Endpoint): HMRCValidated[String] = {
    val isPathParam: String => Boolean = { segment: String => segment.contains("{") || segment.contains("}") }
    val segments                       = endpoint.uriPattern.split("/").toList

    def pathParamValidationError: String => String = {
      s => s"Curly-bracketed segment '$s' should match regular expression '$pathParameterRegex' $errorContext"
    }

    segments
      .filter(isPathParam)
      .map(validateField(_.matches(pathParameterRegex), pathParamValidationError))
      .combineAll
  }

  private def validateQueryParameters(errorContext: String, endpoint: Endpoint): HMRCValidated[List[Parameter]] = {
    validateAll[Parameter](u => queryParameterValidator.validate(errorContext)(u))(endpoint.queryParameters.getOrElse(Seq()))
  }

}
