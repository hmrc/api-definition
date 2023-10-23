package uk.gov.hmrc.apidefinition.services

import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.utils.ApplicationLogger
import scala.util.Success
import scala.util.Failure

class ApiRemover(awsApiPublisherConnector: AWSAPIPublisherConnector) extends ApplicationLogger {
    

    def deleteUnusedApis()(implicit hc: HeaderCarrier, ec: ExecutionContext) : Unit = {
        val unusedApis = List("api1", "api2", "api3")
        logger.warn("Some message")

        def deleteUnusedApisHelper(api: String)(implicit hc: HeaderCarrier) : Future[String] = {
            awsApiPublisherConnector.deleteAPI(api)
        }

        // unusedApis.map{deleteUnusedApisHelper}.foreach(f => f.onComplete({
        //     case Success(value) => //log it
        //     case Failure(exception) => //log failure
        // }))

        // awsApiPublisherConnector.deleteAPI("api1")
        // Load unused-apis-to-be-deleted from config List[String]
        // Iterate through each one and call AWSAPIPublisherConnector.deleteAPI
        // Log success or failure
    }
  
}
