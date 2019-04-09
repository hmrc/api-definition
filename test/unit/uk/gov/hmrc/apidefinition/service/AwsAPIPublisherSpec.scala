package unit.uk.gov.hmrc.apidefinition.service

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.config.AppContext
import uk.gov.hmrc.apidefinition.connector.AWSAPIPublisherConnector
import uk.gov.hmrc.apidefinition.models.WSO2APIDefinition
import uk.gov.hmrc.apidefinition.services.AwsApiPublisher
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future._

class AwsAPIPublisherSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  private trait Setup {
    implicit val hc = HeaderCarrier().withExtraHeaders(xRequestId -> "requestId")

    val underTest = new AwsApiPublisher(mock[AppContext], mock[AWSAPIPublisherConnector])

    when(underTest.appContext.buildProductionUrlForPrototypedAPIs).thenReturn(successful(false))

    implicit val appContext = underTest.appContext
  }

  "createOrUpdate" should {

    "create the API when none exists" in new Setup {
      when(underTest.awsAPIPublisherConnector.doesAPIExist(any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(false))
      when(underTest.awsAPIPublisherConnector.createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))
      when(underTest.awsAPIPublisherConnector.publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier])))
        .thenReturn(successful(()))

      val result = await(underTest.publish(someAPIDefinition))

      verify(underTest.awsAPIPublisherConnector)
        .createAPI(refEq(cookie), any(classOf[WSO2APIDefinition]))(any(classOf[HeaderCarrier]))
      verify(underTest.awsAPIPublisherConnector)
        .publishAPIStatus(refEq(cookie), any(classOf[WSO2APIDefinition]), refEq("PUBLISHED"))(any(classOf[HeaderCarrier]))
    }
}
