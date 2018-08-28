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

package uk.gov.hmrc.apidefinition.validators
import cats.implicits._
import uk.gov.hmrc.apidefinition.models.{AuthType, Endpoint, Parameter}
import uk.gov.hmrc.apidefinition.validators.ApiDefinitionValidator.HMRCValidated

object ApiEndpointValidator extends Validator[Endpoint] {

  private val uriRegex: String = "^/[a-zA-Z0-9_\\-\\/{}]*$"
  private val pathParameterRegex: String = "^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$"

  def validate(ec: String)(implicit endpoint: Endpoint): HMRCValidated[Endpoint] = {
    val errorContext: String = if (endpoint.endpointName.isEmpty) ec else s"$ec and endpoint name '${endpoint.endpointName}'"
    (
      validateThat(_.endpointName.nonEmpty, _ => s"Field 'endpointName' is required $errorContext"),
      validateUriPattern(errorContext),
      validateScope(errorContext),
      validatePathParameters(errorContext, endpoint),
      validateQueryParameters(errorContext, endpoint)
    ).mapN((_,_,_,_,_) => endpoint)
  }

  private def validateUriPattern(errorContext: String)(implicit endpoint: Endpoint): HMRCValidated[Endpoint] = {
    validateThat(_.uriPattern.nonEmpty, _ => s"Field 'uriPattern' is required $errorContext").andThen(
      validateField(_.uriPattern.matches(uriRegex), e => s"Invalid value '${e.uriPattern}' for field 'uriPattern' $errorContext")
    )
  }

  private def validateScope(errorContext: String)(implicit endpoint: Endpoint): HMRCValidated[Endpoint] = {
    endpoint.authType match {
      case AuthType.USER => validateThat(_ => endpoint.scope.nonEmpty, _ => s"Field 'scope' is required $errorContext")
      case AuthType.APPLICATION => validateThat(_ => endpoint.scope.isEmpty, _ => s"Field 'scope' is not allowed $errorContext")
      case _ => endpoint.validNel
    }
  }

  private def validatePathParameters(errorContext: String, endpoint: Endpoint): HMRCValidated[String] = {
    val isPathParam: String => Boolean =  { segment: String => segment.contains("{") || segment.contains("}") }
    val segments = endpoint.uriPattern.split("/").toList
    segments
      .filter(isPathParam)
      .map(validateField[String](_.matches(pathParameterRegex), s => s"'$s' is not a valid segment $errorContext"))
      .combineAll
  }

  private def validateQueryParameters(errorContext: String, endpoint: Endpoint): HMRCValidated[List[Parameter]] = {
    validateAll[Parameter](u => QueryParameterValidator.validate(errorContext)(u))(endpoint.queryParameters.getOrElse(Seq()))
  }
}
