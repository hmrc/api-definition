/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.services

import javax.inject.Inject
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiStatus, ApiVersion, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr

import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

class ApiRetirer @Inject() (apiDefinitionRepository: APIDefinitionRepository)
    extends ApplicationLogger {

  def retireApis(apisToRetire: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Attempting to retire ${apisToRetire.length} API versions.")
    Future.sequence(apisToRetire.filter(isValid).map { apiAndVersion => findAndRetireApi(apiAndVersion) })
      .map(_ => ())
  }

  private def findAndRetireApi(apiAndVersion: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val (api, versionToRetire) = getApiVersion(apiAndVersion)
    val listOfVersions         = ListBuffer[ApiVersion]()

    apiDefinitionRepository.fetchByServiceName(ServiceName(api)) map {
      case Some(definition) => {
        definition.versions.map {
          version =>
            {
              if (version.versionNbr == ApiVersionNbr(versionToRetire)) {
                listOfVersions += version.copy(status = ApiStatus.RETIRED)
              } else {
                listOfVersions += version
              }
            }
        }
        val updatedDefinition = definition.copy(versions = listOfVersions.toList)
        apiDefinitionRepository.save(updatedDefinition)
        logger.debug(s"$api version $versionToRetire saved.")
      }
      case _                => logger.warn(s"$api version $versionToRetire can not be found")
    } recover {
      case NonFatal(e) => logger.warn(s"$api retire failed.", e)
    }
  }

  private def getApiVersion(apiAndVersion: String): (String, String) = {
    val splitString     = apiAndVersion.split(",")
    val api             = splitString(0)
    val versionToRetire = splitString(1)
    (api, versionToRetire)
  }

  private def isValid(apiAndVersion: String): Boolean = {
    val pattern = ".+,.+"
    apiAndVersion.matches(pattern)
  }
}
