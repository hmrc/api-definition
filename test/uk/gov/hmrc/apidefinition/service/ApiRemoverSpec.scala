package uk.gov.hmrc.apidefinition.service
import play.api.Logger

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.services.ApiRemover
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.Mockito

class ApiRemoverSpec extends AsyncHmrcSpec {

    implicit val hc = HeaderCarrier()

    trait Setup {
        val mockLogger: Logger = mock[Logger]
    }

    "deleteUnusedApis" should {
        "call AWSAPIPublisherConnector.deleteAPI for each unused API" in new Setup {
            val publisherConnector = mock[AWSAPIPublisherConnector]

            val unit = new ApiRemover(publisherConnector){
               override val logger: Logger = mockLogger
                    }

            unit.deleteUnusedApis();

            verify(publisherConnector, times(3)).deleteAPI(any[String])(*)
            verifyNoMoreInteractions(publisherConnector)
            Mockito.verify(mockLogger).warn(s"Some message")

        }
    }
  
}

