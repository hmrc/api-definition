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

import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import java.nio.file.Path
import java.nio.file.Paths


object ApiContextValidator extends Validator[ApiContext] {
  private val ValidTopLevelContexts: Set[ApiContext] =
    Set("agents", "customs", "individuals", "mobile", "obligations", "organisations", "test", "payments", "misc", "accounts")
      .map(ApiContext(_))
  
  private val formattedTopLevelContexts = ValidTopLevelContexts.map(_.value).toList.sorted.mkString("'", "', '", "'")

  private val contextRegex: Regex                    = """^[a-z]+[a-z\/\-]{4,}$""".r

  def validateForExistingAPI(apiContext: ApiContext): HMRCValidatedNel[ApiContext] = {
    (
      validateTopLevelContext(apiContext),
      validateContext(apiContext)
    )
    .mapN { case _ => apiContext }
    .leftMap(_.map(s => s"${apiContext.value} - $s"))
  }

  def validationsForNewAPI(apiContext: ApiContext, otherContextsInTopLevel: List[ApiContext]): HMRCValidatedNel[ApiContext] = {
    (
      validateTopLevelContext(apiContext),
      validateContextHasAtLeastTwoSegments(apiContext),
      validateContextDoesNotOverlapExistingAPI(apiContext, otherContextsInTopLevel)
    )
    .mapN { case _ => apiContext }
    .leftMap(_.map(s => s"${apiContext.value} - $s"))
  }

  protected def validateTopLevelContext(apiContext: ApiContext): HMRCValidatedNel[ApiContext] = {
    (apiContext.valid
      .ensure("Field 'context' should not be empty")(_.value.nonEmpty)
      .ensure(s"Field 'context' must start with one of $formattedTopLevelContexts")(c => ValidTopLevelContexts.contains(c.topLevelContext()))
    ).toValidatedNel
  }

  protected def validateContext(apiContext: ApiContext): HMRCValidatedNel[ApiContext] = {
    apiContext.valid
      .ensure("Field 'context' should not have empty path segments")(c => ! c.value.contains("//"))
      .ensure(s"Field 'context' should match regular expression '$contextRegex'")(_.value.matches(contextRegex))
      .toValidatedNel
  }

  protected def validateContextHasAtLeastTwoSegments(apiContext: ApiContext): HMRCValidatedNel[ApiContext] =
    apiContext.valid
    .ensure("Field 'context' must have at least two segments")(_.segments().length > 1)
    .toValidatedNel

  protected def validateContextDoesNotOverlapExistingAPI(apiContext: ApiContext, otherContextsInTopLevel: List[ApiContext]): HMRCValidatedNel[ApiContext] = {
    def overlap(firstPath: Path, secondPath: Path): Boolean = {
      firstPath.startsWith(secondPath) || secondPath.startsWith(firstPath)
    }

    def validateNoOverlap(otherContext: ApiContext): HMRCValidatedNel[ApiContext] = {
      otherContext.valid
        .ensure(s"Field 'context' overlaps with '$otherContext'")(_ => ! overlap(Paths.get(otherContext.value), Paths.get(apiContext.value)))
        .toValidatedNel
    }

    otherContextsInTopLevel
      .map(validateNoOverlap(_).map(_ => List.empty))
      .combineAll
      .map( _ => apiContext)
  }

}
//   def validate(errorContext: String, apiDefinition: StoredApiDefinition)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
//     if (appConfig.skipContextValidationAllowlist.contains(apiDefinition.serviceName)) {
//       successful(context.validNel)
//     } else {
//       val validated = validateThat(_.value.nonEmpty, _ => s"Field 'context' should not be empty $errorContext").andThen(validateContext(errorContext)(_))
//       validated match {
//         case Invalid(_) => successful(validated)
//         case _          => validateAgainstDatabase(errorContext, apiDefinition)
//       }
//     }
//   }

//   private def validateAgainstDatabase(errorContext: String, apiDefinition: StoredApiDefinition)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
//     val existingAPIDefinitionFuture: Future[Option[ApiDefinition]] = apiDefinitionService.fetchByContext(apiDefinition.context)
//     for {
//       contextUniqueValidated         <- validateFieldNotAlreadyUsed(existingAPIDefinitionFuture, s"Field 'context' must be unique $errorContext")(context, apiDefinition)
//       contextNotChangedValidated     <- validateContextNotChanged(errorContext, apiDefinition)
//       newAPITopLevelContextValidated <- existingAPIDefinitionFuture.flatMap(existingAPIDefinition =>
//                                           existingAPIDefinition match {
//                                             case None    => validationsForNewAPI(errorContext)
//                                             case Some(_) => successful(context.validNel)
//                                           }
//                                         )
//     } yield (contextUniqueValidated, contextNotChangedValidated, newAPITopLevelContextValidated).mapN((_, _, _) => context)
//   }

//   private def validateContextNotChanged(errorContext: String, apiDefinition: StoredApiDefinition)(implicit context: ApiContext): Future[HMRCValidated[ApiContext]] = {
//     apiDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)
//       .map {
//         case Some(found: StoredApiDefinition) => found.context != apiDefinition.context
//         case _                                => false
//       }.map(contextChanged => validateThat(_ => !contextChanged, _ => s"Field 'context' must not be changed $errorContext"))
//   }
