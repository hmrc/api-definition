package uk.gov.hmrc.apidefinition.services

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.StoredApiDefinition
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import scala.collection.mutable.ListBuffer
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiVersion

class ApiRetirer @Inject() (config: AppConfig, apiDefinitionRepository: APIDefinitionRepository)
    extends ApplicationLogger {

  def retireApis()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Attempting to retire ${config.apisToRetire.length} APIs.")
    Future.sequence(config.apisToRetire.map { api => findAndRetireApi(api) })
      .map(_ => ())
  }

  private def findAndRetireApi(apiAndVersion: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val splitString = apiAndVersion.split(",")
    val api = splitString(0)
    val versionToRetire = splitString(1)
    val listOfVersions = ListBuffer[ApiVersion]()

    apiDefinitionRepository.fetchByServiceName(ServiceName(api)) map {
      case Some(definition) => definition.versions.foreach {
        version => {
          if (version.versionNbr == ApiVersionNbr(versionToRetire)) {
            val updatedVersion = version.copy(status = ApiStatus.RETIRED)
            listOfVersions += updatedVersion
          }
          else {
            listOfVersions += version
          }
        }
        val updatedDefinition = definition.copy(versions = listOfVersions.toList)
        apiDefinitionRepository.save(updatedDefinition)
      } 
    }
  }
}
