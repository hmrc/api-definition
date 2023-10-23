package uk.gov.hmrc.apidefinition.service

import play.api.Logger

import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apidefinition.services.ApiRemover
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class ApiRemoverSpec extends AsyncHmrcSpec {

  implicit val hc = HeaderCarrier()

  trait Setup {
    val mockLogger: Logger = mock[Logger]
    val publisherConnector = mock[AWSAPIPublisherConnector]

    val unit = new ApiRemover(publisherConnector) {
      override val logger: Logger = mockLogger
    }
  }

  "deleteUnusedApis" should {
    "call AWSAPIPublisherConnector.deleteAPI for each unused API" in new Setup {

      unit.deleteUnusedApis()

      verify(publisherConnector, times(3)).deleteAPI(any[String])(*)
      verifyNoMoreInteractions(publisherConnector)
      verify(mockLogger).warn(s"Some message")
    }

    "log message on success" in new Setup {
      when(publisherConnector.deleteAPI("api1")(hc))
        .thenReturn(successful("api1 request ID"))
      when(publisherConnector.deleteAPI("api2")(hc))
        .thenReturn(successful("api2 request ID"))
      when(publisherConnector.deleteAPI("api3")(hc))
        .thenReturn(successful("api3 request ID"))

      unit.deleteUnusedApis()

      verify(publisherConnector, times(3)).deleteAPI(any[String])(*)
      verifyNoMoreInteractions(publisherConnector)
      verify(mockLogger).info(s"api1 successfully deleted. (Request ID: api1 request ID)")
      verify(mockLogger).info(s"api2 successfully deleted. (Request ID: api2 request ID)")
      verify(mockLogger).info(s"api3 successfully deleted. (Request ID: api3 request ID)")
      verifyNoMoreInteractions(mockLogger)
    }

    "log message on failure" in new Setup {
        val err1 = new RuntimeException()
        val err2 = new RuntimeException()
        val err3 = new RuntimeException()
      when(publisherConnector.deleteAPI("api1")(hc))
        .thenReturn(failed(err1))
      when(publisherConnector.deleteAPI("api2")(hc))
        .thenReturn(failed(err2))
      when(publisherConnector.deleteAPI("api3")(hc))
        .thenReturn(failed(err3))

      unit.deleteUnusedApis()

      verify(publisherConnector, times(3)).deleteAPI(any[String])(*)
      verifyNoMoreInteractions(publisherConnector)
      verify(mockLogger).warn(s"api1 delete failed.", err1)
      verify(mockLogger).warn(s"api2 delete failed.", err2)
      verify(mockLogger).warn(s"api3 delete failed.", err3)
      verifyNoMoreInteractions(mockLogger)
    }
  }

}
