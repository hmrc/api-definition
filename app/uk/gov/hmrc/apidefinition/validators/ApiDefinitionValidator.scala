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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{StoredApiDefinition, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}

object ApiDefinitionValidator extends Validator[StoredApiDefinition] {

  def validateKeysArePresent(apiDefinition: StoredApiDefinition): HMRCValidatedNel[StoredApiDefinition] = {
    (
      apiDefinition.serviceName.validNel.ensure("Field 'serviceName' should not be empty".nel)(_.value.nonBlank),
      apiDefinition.context.validNel.ensure("Field 'context' should not be empty".nel)(_.value.nonBlank),
      apiDefinition.serviceBaseUrl.validNel.ensure("Field 'serviceBaseUrl' should not be empty".nel)(_.nonBlank),
      apiDefinition.name.validNel.ensure("Field 'name' should not be empty".nel)(_.nonBlank)
    )
      .mapN { case _ => apiDefinition }
  }

  def validate(
      requestedDefn: StoredApiDefinition,
      oExistingApiDefn: Option[StoredApiDefinition],
      byContext: Option[StoredApiDefinition],
      byServiceBaseUrl: Option[StoredApiDefinition],
      byName: Option[StoredApiDefinition],
      otherContextsWithSameTopLevel: List[ApiContext],
      skipContextValidation: Boolean
    ): HMRCValidatedNel[StoredApiDefinition] = {
    oExistingApiDefn.fold(
      validateNewAPI(skipContextValidation)(requestedDefn, byContext, byServiceBaseUrl, byName, otherContextsWithSameTopLevel)
    )(c => validateExistingAPI(skipContextValidation)(requestedDefn, c))
  }

  def validateOtherFields(requestedDefn: StoredApiDefinition): HMRCValidatedNel[StoredApiDefinition] = {
    (
      requestedDefn.description.validNel.ensure("Field 'description' should not be empty".nel)(_.nonBlank),
      requestedDefn.categories.validNel.ensure("Field 'categories' should not be empty".nel)(_.nonEmpty),
      requestedDefn.versions.validNel
        .ensure("Field 'versions' should not be empty".nel)(_.nonEmpty)
        .ensure("Field 'version' must be unique".nel)(vs => uniqueVersionsPredicate(vs.map(_.versionNbr)))
        .andThen(validateAllVersions)
    )
      .mapN { case _ => requestedDefn }
  }

  private val uniqueVersionsPredicate = (versionNbrs: List[ApiVersionNbr]) => {
    !(
      versionNbrs.groupBy(identity)
        .view.mapValues(_.size)
        .exists(_._2 > 1)
    )
  }

  private def validateAllVersions(versions: List[ApiVersion]): HMRCValidatedNel[List[ApiVersion]] = {
    versions
      .map(v => ApiVersionValidator.validate(v).map(_ :: Nil))
      .combineAll
  }

  private def validateExistingAPI(skipContextValidation: Boolean)(requestedDefn: StoredApiDefinition, existingApiDefn: StoredApiDefinition): HMRCValidatedNel[StoredApiDefinition] = {
    (
      requestedDefn.context.validNel.ensure(s"Field 'context' cannot change from the previously published ${existingApiDefn.context}".nel)(_ == existingApiDefn.context)
        .andThen(ApiContextValidator.validateForExistingAPI(skipContextValidation)(_)),
      requestedDefn.serviceBaseUrl.validNel.ensure(s"Field 'serviceBaseUrl' cannot change from the previously published ${existingApiDefn.serviceBaseUrl}".nel)(
        _ == existingApiDefn.serviceBaseUrl
      ),
      requestedDefn.name.validNel.ensure(s"Field 'name' cannot change from the previously published ${existingApiDefn.name}".nel)(_ == existingApiDefn.name),
      determineMissingVersions(requestedDefn, existingApiDefn).validNel.ensureOr(missingVersions =>
        s"Versions (${missingVersions.mkString(", ")}) may not be removed once published".nel
      )(_.isEmpty)
    )
      .mapN { case _ => requestedDefn }
  }

  private def determineMissingVersions(apiDefinition: StoredApiDefinition, existingApiDefn: StoredApiDefinition): List[ApiVersionNbr] = {
    val existingVersions = existingApiDefn.versions.map(_.versionNbr)
    val newVersions      = apiDefinition.versions.map(_.versionNbr)
    existingVersions diff newVersions
  }

  private def validateNewAPI(
      skipContextValidation: Boolean
    )(
      requestedDefn: StoredApiDefinition,
      byContext: Option[StoredApiDefinition],
      byServiceBaseUrl: Option[StoredApiDefinition],
      byName: Option[StoredApiDefinition],
      otherContextsWithSameTopLevel: List[ApiContext]
    ): HMRCValidatedNel[StoredApiDefinition] = {
    (
      byContext.validNel.ensureOr(other => s"Field 'context' must be unique but is used by ${other.get.serviceName}".nel)(_.isEmpty)
        .andThen(_ => ApiContextValidator.validateForNewAPI(skipContextValidation)(requestedDefn.context, otherContextsWithSameTopLevel)),
      byServiceBaseUrl.validNel.ensureOr(other => s"Field 'serviceBaseUrl' must be unique but is used by ${other.get.serviceName}".nel)(_.isEmpty),
      byName.validNel.ensureOr(other => s"Field 'name' must be unique but is used by ${other.get.serviceName}".nel)(_.isEmpty)
    )
      .mapN { case _ => requestedDefn }
  }
}
