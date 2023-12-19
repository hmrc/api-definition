package uk.gov.hmrc.apidefinition.services

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext

class ApiRetirer @Inject() (config: AppConfig) extends ApplicationLogger {
  
  def retireApis()(implicit ec: ExecutionContext): Future[String] = {
    successful("hi")
    // logger.info(s"Attempting to retire ${config.apisToRetire.length} APIs.")
  //   Future.sequence(config.apisToRetire.map { api => findAndRetireApi(api) })
  //     .map(_ => ())
  // }

  // private def findAndRetireApi(api: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
  //   print("hi")
  //   }
  }
}