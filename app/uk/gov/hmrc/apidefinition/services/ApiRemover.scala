package uk.gov.hmrc.apidefinition.services

import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class ApiRemover(awsApiPublisherConnector: AWSAPIPublisherConnector) {

    def deleteUnusedApis()(implicit hc: HeaderCarrier) : List[Future[String]] = {
        val unusedApis = List("api1", "api2", "api3")

        def deleteUnusedApisHelper(api: String)(implicit hc: HeaderCarrier) : Future[String] = {
            val temp : Future[String] = awsApiPublisherConnector.deleteAPI(api)(hc)
            return temp
        }


        val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val res = numbers.foldLeft(0)((m, n) => m + n)
        
        println(res) // 55



        unusedApis.map{deleteUnusedApisHelper}

        // awsApiPublisherConnector.deleteAPI("api1")
        // Load unused-apis-to-be-deleted from config List[String]
        // Iterate through each one and call AWSAPIPublisherConnector.deleteAPI
        // Log success or failure
    }
  
}
