package uk.gov.hmrc.apidefinition.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.services.ApiRemover
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiRemoverSpec extends AsyncHmrcSpec {

  implicit val hc = HeaderCarrier()

  trait Setup {
    val mockLogger: Logger       = mock[Logger]
    val publisherConnector       = mock[AWSAPIPublisherConnector]
    val mockAppConfig: AppConfig = mock[AppConfig]

    val unit = new ApiRemover(publisherConnector, mockAppConfig) {
      override val logger: Logger = mockLogger
    }
  }

  "deleteUnusedApis" should {
    "call AWSAPIPublisherConnector.deleteAPI for each unused API and log message on success" in new Setup {
      when(mockAppConfig.apisToRemove).thenReturn(List("api1", "api2", "api3"))
      when(publisherConnector.deleteAPI("api1")(hc))
        .thenReturn(successful("api1 request ID"))
      when(publisherConnector.deleteAPI("api2")(hc))
        .thenReturn(successful("api2 request ID"))
      when(publisherConnector.deleteAPI("api3")(hc))
        .thenReturn(successful("api3 request ID"))

      unit.deleteUnusedApis()

      verify(publisherConnector, times(3)).deleteAPI(any[String])(*)
      verifyNoMoreInteractions(publisherConnector)
      verify(mockLogger).info(s"Attempting to delete 3 unused APIs.")
      verify(mockLogger).info(s"api1 successfully deleted. (Request ID: api1 request ID)")
      verify(mockLogger).info(s"api2 successfully deleted. (Request ID: api2 request ID)")
      verify(mockLogger).info(s"api3 successfully deleted. (Request ID: api3 request ID)")
      verifyNoMoreInteractions(mockLogger)
    }

    "call AWSAPIPublisherConnector.deleteAPI for each unused API and log message on failure" in new Setup {
      when(mockAppConfig.apisToRemove).thenReturn(List("api1", "api2", "api3"))
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
      verify(mockLogger).info(s"Attempting to delete 3 unused APIs.")
      verify(mockLogger).warn(s"api1 delete failed.", err1)
      verify(mockLogger).warn(s"api2 delete failed.", err2)
      verify(mockLogger).warn(s"api3 delete failed.", err3)
      verifyNoMoreInteractions(mockLogger)
    }

    "do nothing when the list of APIs to remove is empty" in new Setup {
      when(mockAppConfig.apisToRemove).thenReturn(List())

      unit.deleteUnusedApis()

      verifyZeroInteractions(publisherConnector)
    }

    "log the number of APIs to be removed" in new Setup {
        when(mockAppConfig.apisToRemove).thenReturn(List("1","2","3","4"))
        when(publisherConnector.deleteAPI(*)(*)).thenReturn(successful(""))

        unit.deleteUnusedApis()

        verify(mockLogger).info(s"Attempting to delete 4 unused APIs.")
    }
  }
}
