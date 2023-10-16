package uk.gov.hmrc.apidefinition.service

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.services.ApiRemover
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector

class ApiRemoverSpec extends AsyncHmrcSpec {

    "deleteUnusedApis" should {
        "call AWSAPIPublisherConnector.deleteAPI for each unused API" in {
            val publisherConnector = mock[AWSAPIPublisherConnector]

            val unit = new ApiRemover(publisherConnector);

            unit.deleteUnusedApis();

            verify(publisherConnector, times(3)).deleteAPI(any[String])(*)

            // Mock AWSAPIPublisherConnector.deleteAPI
            // Call deleteUnusedApis
            // Verify AWSAPIPublisherConnector.deleteAPI was called for each unused API
        }
    }
  
}
