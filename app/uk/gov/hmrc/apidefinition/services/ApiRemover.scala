package uk.gov.hmrc.apidefinition.services

import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext

class ApiRemover(awsApiPublisherConnector: AWSAPIPublisherConnector) extends ApplicationLogger {
    
    def deleteUnusedApis()(implicit hc: HeaderCarrier, ec: ExecutionContext) : Unit = {
        val unusedApis = List("api1", "api2", "api3")

        def deleteUnusedApisHelper(api: String)(implicit hc: HeaderCarrier) : Unit = {
            awsApiPublisherConnector.deleteAPI(api).onComplete({
                case Success(requestId) => logger.info(s"$api successfully deleted. (Request ID: $requestId)")
                case Failure(exception) => logger.warn(s"$api delete failed.", exception)
            })
        }

        unusedApis.map{api => deleteUnusedApisHelper(api)}
    }
}
