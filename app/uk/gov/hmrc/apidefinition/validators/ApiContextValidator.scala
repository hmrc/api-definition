/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.data.Validated.Invalid
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

@Singleton
class ApiContextValidator @Inject()(apiDefinitionService: APIDefinitionService,
                                    apiDefinitionRepository: APIDefinitionRepository)
                                   (implicit override val ec: ExecutionContext) extends Validator[String] {

  private val ValidTopLevelContexts: Set[String] = Set("agents", "customs", "individuals", "organisations")
  private val contextRegex: Regex = "^[a-zA-Z0-9_\\-\\/]+$".r

  def validate(errorContext: String, apiDefinition: APIDefinition)(implicit context: String): Future[HMRCValidated[String]] = {
    val validated = validateThat(_.nonEmpty, _ => s"Field 'context' should not be empty $errorContext").andThen(validateContext(errorContext)(_))
    validated match {
      case Invalid(_) => successful(validated)
      case _ => validateAgainstDatabase(errorContext, apiDefinition)
    }
  }

  private def validateContext(errorContext: String)(implicit context: String): HMRCValidated[String] = {
    (
      validateThat(!_.startsWith("/"), _ => s"Field 'context' should not start with '/' $errorContext"),
      validateThat(!_.endsWith("/"), _ => s"Field 'context' should not end with '/' $errorContext"),
      validateThat(!_.contains("//"), _ => s"Field 'context' should not have empty path segments $errorContext"),
      validateThat(_.matches(contextRegex), _ => s"Field 'context' should match regular expression '$contextRegex' $errorContext")
    ).mapN((_,_,_,_) => context)
  }

  private def validateAgainstDatabase(errorContext: String, apiDefinition: APIDefinition)(implicit context: String): Future[HMRCValidated[String]] = {
    val existingAPIDefinitionFuture: Future[Option[APIDefinition]] = apiDefinitionService.fetchByContext(apiDefinition.context)
    for {
      contextUniqueValidated
        <- validateFieldNotAlreadyUsed(existingAPIDefinitionFuture, s"Field 'context' must be unique $errorContext")(context, apiDefinition)
      contextNotChangedValidated <- validateContextNotChanged(errorContext, apiDefinition)
      newAPITopLevelContextValidated
        <- existingAPIDefinitionFuture.flatMap(existingAPIDefinition => validateTopLevelContextForNewAPIs(errorContext, existingAPIDefinition))
    } yield (contextUniqueValidated, contextNotChangedValidated, newAPITopLevelContextValidated).mapN((_, _, _) => context)
  }

  private def validateContextNotChanged(errorContext: String, apiDefinition: APIDefinition)(implicit context: String): Future[HMRCValidated[String]] = {
    apiDefinitionRepository.fetchByServiceName(apiDefinition.serviceName)
      .map {
        case Some(found: APIDefinition) => found.context != apiDefinition.context
        case _ => false
      }.map(contextChanged => validateThat(_ => !contextChanged, _ => s"Field 'context' must not be changed $errorContext"))
  }

  private def validateTopLevelContextForNewAPIs(errorContext: String,
                                                existingAPIDefinitionOption: Option[APIDefinition])
                                               (implicit context: String): Future[HMRCValidated[String]] = {
    def topLevelContext: String = context.split('/').head
    def formattedTopLevelContexts: String = ValidTopLevelContexts.mkString("'","', '", "'")

    successful(
      existingAPIDefinitionOption match {
        case None =>
          validateThat(
            _ => ValidTopLevelContexts.contains(topLevelContext),
            _ => s"Field 'context' must start with one of $formattedTopLevelContexts $errorContext")
        case Some(_) => context.validNel
      }
    )

  }
}
