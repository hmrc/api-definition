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

package uk.gov.hmrc.apidefinition.validators

import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.mvc.Results.UnprocessableEntity
import uk.gov.hmrc.apidefinition.models.ErrorCode.INVALID_REQUEST_PAYLOAD
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.services.APIDefinitionService

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiDefinitionValidator @Inject()(apiDefinitionService: APIDefinitionService,
                                       apiContextValidator: ApiContextValidator,
                                       apiVersionValidator: ApiVersionValidator)
                                      (implicit override val ec: ExecutionContext) extends Validator[APIDefinition] {

  def validate(apiDefinition: APIDefinition)(f: APIDefinition => Future[Result]): Future[Result] = {
    validateDefinition(apiDefinition).flatMap {
      _ match {
        case Valid(validDefinition) => f(validDefinition)
        case Invalid(errors) => successful(UnprocessableEntity(toJson(ValidationErrors(INVALID_REQUEST_PAYLOAD, errors.toList))))
      }
    }
  }

  def validateDefinition(implicit apiDefinition: APIDefinition): Future[HMRCValidated[APIDefinition]] = {
    val errorContext: String =
      if (apiDefinition.name.isEmpty) s"for API with service name '${apiDefinition.serviceName}'"
      else s"for API '${apiDefinition.name}'"

    for {
      contextValidated <- apiContextValidator.validate(errorContext, apiDefinition)(apiDefinition.context)
      nameValidated <- validateName(errorContext)
      serviceBaseUrlValidated <- validateServiceBaseUrl(errorContext)

      validated: HMRCValidated[APIDefinition] = (
        validateThat(_.serviceName.nonEmpty, _ => s"Field 'serviceName' should not be empty $errorContext"),
        validateThat(_.description.nonEmpty, _ => s"Field 'description' should not be empty $errorContext"),
        validateThat(_.categories.exists(_.nonEmpty), _ => s"Field 'categories' should exist and not be empty $errorContext"),
        contextValidated,
        nameValidated,
        serviceBaseUrlValidated,
        validateVersions(errorContext)
      ).mapN((_, _, _, _, _, _, _) => apiDefinition)

    } yield validated
  }

  private def validateName(errorContext: String)(implicit apiDefinition: APIDefinition): Future[HMRCValidated[APIDefinition]] = {
    val validated = validateThat(_.name.nonEmpty, _ => s"Field 'name' should not be empty $errorContext")
    validated match {
      case Invalid(_) => successful(validated)
      case _ => validateFieldNotAlreadyUsed(apiDefinitionService.fetchByName(apiDefinition.name),
        s"Field 'name' must be unique $errorContext")
    }
  }

  private def validateServiceBaseUrl(errorContext: String)(implicit apiDefinition: APIDefinition): Future[HMRCValidated[APIDefinition]] ={
    val validated = validateThat(_.serviceBaseUrl.nonEmpty, _ => s"Field 'serviceBaseUrl' should not be empty $errorContext")
    validated match {
      case Invalid(_) => successful(validated)
      case _ => validateFieldNotAlreadyUsed(apiDefinitionService.fetchByServiceBaseUrl(apiDefinition.serviceBaseUrl),
        s"Field 'serviceBaseUrl' must be unique $errorContext")
    }
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
    validateAll[APIVersion](u => apiVersionValidator.validate(errorContext)(u))(apiDefinition.versions)
  }
}
