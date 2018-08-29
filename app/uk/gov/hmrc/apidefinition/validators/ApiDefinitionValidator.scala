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

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.mvc.Results.UnprocessableEntity
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models._

import scala.concurrent.Future

object ApiDefinitionValidator extends Validator[APIDefinition] {

  type HMRCValidated[A] = ValidatedNel[String, A]

  def validate(apiDefinition: APIDefinition)(f: APIDefinition => Future[Result]): Future[Result] = {
    validateDefinition(apiDefinition) match {
      case Valid(validDefinition) => f(validDefinition)
      case Invalid(errors) => Future.successful(UnprocessableEntity(toJson(ValidationErrors(INVALID_REQUEST_PAYLOAD, errors.toList))))
    }
  }

  def validateDefinition(implicit apiDefinition: APIDefinition): HMRCValidated[APIDefinition] = {
    val errorContext: String =
      if (apiDefinition.name.isEmpty) s"for API with service name '${apiDefinition.serviceName}'"
      else s"for API '${apiDefinition.name}'"

    (
      validateThat(_.serviceName.nonEmpty, _ => s"Field 'serviceName' should not be empty $errorContext"),
      validateThat(_.serviceBaseUrl.nonEmpty, _ => s"Field 'serviceBaseUrl' should not be empty $errorContext"),
      validateThat(_.name.nonEmpty, _ => s"Field 'name' should not be empty $errorContext"),
      validateThat(_.description.nonEmpty, _ => s"Field 'description' should not be empty $errorContext"),
      ApiContextValidator.validate(errorContext)(apiDefinition.context),
      validateVersions(errorContext)
    ).mapN((_, _, _, _, _, _) => apiDefinition)
  }

  private def validateVersions(errorContext: String)(implicit apiDefinition: APIDefinition): HMRCValidated[APIDefinition] = {
    validateThat(_.versions.nonEmpty, _ => s"Field 'versions' must not be empty $errorContext")
      .andThen(ad =>
        (validateUniqueVersions(errorContext)(ad), validateAllVersions(errorContext)(ad)).mapN((_,_) => ad))
  }

  private def validateUniqueVersions(errorContext: String)(implicit apiDefinition: APIDefinition): HMRCValidated[APIDefinition] = {
    validateThat(uniqueVersionsPredicate, _ => s"Field 'version' must be unique $errorContext")
  }

  private def uniqueVersionsPredicate(definition: APIDefinition): Boolean = {
    !definition.versions.map(_.version).groupBy(identity).mapValues(_.size).exists(_._2 > 1)
  }

  private def validateAllVersions(errorContext: String)(apiDefinition: APIDefinition): HMRCValidated[List[APIVersion]] = {
    validateAll[APIVersion](u => ApiVersionValidator.validate(errorContext)(u))(apiDefinition.versions)
  }
}
