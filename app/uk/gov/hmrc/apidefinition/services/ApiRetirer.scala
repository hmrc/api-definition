package uk.gov.hmrc.apidefinition.services

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class ApiRetirer @Inject() (config: AppConfig) extends ApplicationLogger {
  
  def SayHello()(implicit ec: ExecutionContext): Future[String] = {
    return  Future("hello")
  }
}
