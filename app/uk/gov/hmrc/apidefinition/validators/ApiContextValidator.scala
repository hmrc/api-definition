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

import java.nio.file.{Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

import cats.Monoid._
import cats.data.Validated.Invalid
import cats.implicits._
import cats.kernel.Monoid

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.StoredApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService

@Singleton
class ApiContextValidator @Inject() (
    apiDefinitionService: APIDefinitionService,
    apiDefinitionRepository: APIDefinitionRepository,
    appConfig: AppConfig
  )(implicit override val ec: ExecutionContext
  ) extends Validator[ApiContext] {

  private val ValidTopLevelContexts: Set[ApiContext] =
    Set("agents", "customs", "individuals", "mobile", "obligations", "organisations", "test", "payments", "misc", "accounts").map(ApiContext(_))
  private val contextRegex: Regex                    = """^[a-zA-Z0-9_\-\/]+$""".r

  def validate(errorContext: String, apiDefinition: StoredApiDefinition)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
    if (appConfig.skipContextValidationAllowlist.contains(apiDefinition.serviceName)) {
      successful(context.validNel)
    } else {
      val validated = validateThat(_.value.nonEmpty, _ => s"Field 'context' should not be empty $errorContext").andThen(validateContext(errorContext)(_))
      validated match {
        case Invalid(_) => successful(validated)
        case _          => validateAgainstDatabase(errorContext, apiDefinition)
      }
    }
  }

  private def validateContext(errorContext: String)(implicit context: ApiContext): HMRCValidated[ApiContext] = {
    (
      validateThat(!_.value.startsWith("/"), _ => s"Field 'context' should not start with '/' $errorContext"),
      validateThat(!_.value.endsWith("/"), _ => s"Field 'context' should not end with '/' $errorContext"),
      validateThat(!_.value.contains("//"), _ => s"Field 'context' should not have empty path segments $errorContext"),
      validateThat(_.value.matches(contextRegex), _ => s"Field 'context' should match regular expression '$contextRegex' $errorContext")
    ).mapN((_, _, _, _) => context)
  }

  private def validateAgainstDatabase(errorContext: String, apiDefinition: StoredApiDefinition)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
    val existingAPIDefinitionFuture: Future[Option[StoredApiDefinition]] = apiDefinitionService.fetchByContext(apiDefinition.context)
    for {
      contextUniqueValidated         <- validateFieldNotAlreadyUsed(existingAPIDefinitionFuture, s"Field 'context' must be unique $errorContext")(context, apiDefinition)
      contextNotChangedValidated     <- validateContextNotChanged(errorContext, apiDefinition)
      newAPITopLevelContextValidated <- existingAPIDefinitionFuture.flatMap(existingAPIDefinition =>
                                          existingAPIDefinition match {
                                            case None    => validationsForNewAPI(errorContext)
                                            case Some(_) => successful(context.validNel)
                                          }
                                        )
    } yield (contextUniqueValidated, contextNotChangedValidated, newAPITopLevelContextValidated).mapN((_, _, _) => context)
  }

  private def validateContextNotChanged(errorContext: String, apiDefinition: StoredApiDefinition)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
    apiDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)
      .map {
        case Some(found: StoredApiDefinition) => found.context != apiDefinition.context
        case _                          => false
      }.map(contextChanged => validateThat(_ => !contextChanged, _ => s"Field 'context' must not be changed $errorContext"))
  }

  private def validationsForNewAPI(errorContext: String)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
    for {
      validTopLevelContext      <- validateTopLevelContext(errorContext)
      atLeastTwoContextSegments <- validateContextHasAtLeastTwoSegments(errorContext)
      noContextOverlaps         <- validateContextDoesNotOverlapExistingAPI(errorContext)
    } yield (validTopLevelContext, atLeastTwoContextSegments, noContextOverlaps).mapN((_, _, _) => context)
  }

  private def validateTopLevelContext(errorContext: String)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
    def formattedTopLevelContexts: String = ValidTopLevelContexts.toList.map(_.value).sorted.mkString("'", "', '", "'")

    successful(validateThat(
      _ => ValidTopLevelContexts.contains(context.topLevelContext()),
      _ => s"Field 'context' must start with one of $formattedTopLevelContexts $errorContext"
    ))
  }

  private def validateContextHasAtLeastTwoSegments(errorContext: String)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] =
    successful(validateThat(
      _ => context.segments().length > 1,
      _ => s"Field 'context' must have at least two segments $errorContext"
    ))

  private def validateContextDoesNotOverlapExistingAPI(errorContext: String)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
    def overlap(firstPath: Path, secondPath: Path): Boolean = {
      firstPath.startsWith(secondPath) || secondPath.startsWith(firstPath)
    }

    implicit val fakeApiContextMonoid: Monoid[ApiContext] = new Monoid[ApiContext] {
      def empty: ApiContext                                 = ApiContext("")
      def combine(x: ApiContext, y: ApiContext): ApiContext = ApiContext(x.value)
    }

    for {
      existingAPIDefinitions <- apiDefinitionRepository.fetchAllByTopLevelContext(context.topLevelContext()).map(_.filterNot(_.context == context))
      existingContexts        = existingAPIDefinitions.map(_.context)
      validations             = existingContexts.map(otherContext =>
                                  validateThat(
                                    _ => !overlap(Paths.get(otherContext.value), Paths.get(context.value)),
                                    _ => s"Field 'context' overlaps with '$otherContext' $errorContext"
                                  )
                                )
    } yield combineAll(validations)
    /* API-4094: The combineAll() here actually concatenates the 'context' String multiple times, so any valid HMRCValidation object returned is technically
     * incorrect. However, as we discard the value upstream, this still works. If in future we need to work with the value returned here, this will need
     * to be corrected. Anything failing validation here is not affected.
     */
  }

}
