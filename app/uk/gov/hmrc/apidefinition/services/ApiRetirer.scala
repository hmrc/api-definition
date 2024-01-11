package uk.gov.hmrc.apidefinition.services

import javax.inject.Inject
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiStatus, ApiVersion, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger

class ApiRetirer @Inject() (config: AppConfig, apiDefinitionRepository: APIDefinitionRepository)
    extends ApplicationLogger {

  def retireApis()(implicit ec: ExecutionContext): Future[Unit] = {
    if (config.apisToRetire != null && config.apisToRetire.length != 0) {
      logger.info(s"Attempting to retire ${config.apisToRetire.length} API versions.")
      val filtered = config.apisToRetire.filter(isValid)
      println("************" + filtered + "*******************")
      return Future.sequence(filtered.map { api => findAndRetireApi(api) })
        .map(_ => ())
    }
    else {
      return Future.successful(())
    }
  }

  private def findAndRetireApi(apiAndVersion: String)(implicit ec: ExecutionContext): Future[Unit] = {
    println("********************** findAndRetireApi called with " + apiAndVersion + "***************************" )
    val (api, versionToRetire) = getApiVersion(apiAndVersion)
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
      } 
      val updatedDefinition = definition.copy(versions = listOfVersions.toList)
      apiDefinitionRepository.save(updatedDefinition)
      case _ => logger.warn(s"$api version $versionToRetire can not be found")
    }
  }

  private def getApiVersion(apiAndVersion: String): (String, String) = {
    val splitString = apiAndVersion.split(",")
    val api = splitString(0)
    val versionToRetire = splitString(1)
    return (api, versionToRetire)
  }

  private def isValid(apiAndVersion: String): Boolean = {
    val pattern = """[A-Za-z0-9]+,[0-9]*\.[0-9]+"""
    apiAndVersion.matches(pattern)
  }
}