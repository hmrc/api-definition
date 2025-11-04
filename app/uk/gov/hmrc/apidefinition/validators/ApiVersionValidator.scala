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

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

@Singleton
class ApiVersionValidator @Inject() (apiEndpointValidator: ApiEndpointValidator)(implicit override val ec: ExecutionContext) extends Validator[ApiVersion] {

  def validate(ec: String)(implicit version: ApiVersion): HMRCValidated[ApiVersion] = {
    val errorContext: String = if (version.versionNbr.value.isEmpty) ec else s"$ec version '${version.versionNbr}'"
    (
      validateThat(_.versionNbr.value.nonEmpty, _ => s"Field 'versions.version' is required $errorContext"),
      validateThat(_.endpoints.nonEmpty, _ => s"Field 'versions.endpoints' must not be empty $errorContext"),
      validateStatus(errorContext),
      validateAll[Endpoint](u => apiEndpointValidator.validate(errorContext)(u))(version.endpoints),
      validateUniqueEndpointPaths(errorContext)
    ).mapN((_, _, _, _, _) => version)
  }

  private def validateStatus(errorContext: String)(implicit version: ApiVersion): HMRCValidated[ApiVersion] = {
    version.status match {
      case ApiStatus.ALPHA => validateThat(_ => version.endpointsEnabled == false, _ => s"Field 'versions.endpointsEnabled' must be false for ALPHA status")
      case _               => version.validNel
    }
  }

  private def validateUniqueEndpointPaths(errorContext: String)(implicit version: ApiVersion): HMRCValidated[ApiVersion] = {
    def segments(path: String): List[String] = path.split("/").filter(_.nonEmpty).toList
    def isVariable(segment: String): Boolean = segment.startsWith("{") && segment.endsWith("}")

    val invalidPairsOfUris = version.endpoints
      .map(_.uriPattern)
      .combinations(2)
      .map { case List(uri1, uri2) => (uri1, uri2) }
      .filter { case (uri1, uri2) =>
        segments(uri1).zip(segments(uri2))
          .takeWhile { case (s1, s2) => isVariable(s1) && isVariable(s2) || s1 == s2 }
          .exists { case (s1, s2) => isVariable(s1) && isVariable(s2) && s1 != s2 }
      }
      .toList
    if (invalidPairsOfUris.isEmpty) {
      version.validNel
    } else {
      s"Clashing endpoints $errorContext: $invalidPairsOfUris".invalidNel
    }
  }
}
