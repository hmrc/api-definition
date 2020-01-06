/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package unit.uk.gov.hmrc.apidefinition.scheduled

import org.joda.time.Duration
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => isEq}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.mockito.MockitoSugar
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.scheduled.DeleteApisJob.ApisToDelete
import uk.gov.hmrc.apidefinition.scheduled.{DeleteApisJob, DeleteApisJobConfig, DeleteApisJobLockKeeper}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.{ExecutionContext, Future}

class DeleteApisJobSpec extends UnitSpec with MockitoSugar with MongoSpecSupport {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val lockKeeperSuccess: () => Boolean = () => true
    private val reactiveMongoComponent = new ReactiveMongoComponent {
      override def mongoConnector: MongoConnector = mongoConnectorForTest
    }

    val mockLockKeeper: DeleteApisJobLockKeeper = new DeleteApisJobLockKeeper(reactiveMongoComponent) {
      override def lockId: String = "testLock"

      override def repo: LockRepository = mock[LockRepository]

      override val forceLockReleaseAfter: Duration = Duration.standardMinutes(5) // scalastyle:off magic.number

      override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        if (lockKeeperSuccess()) body.map(value => successful(Some(value)))
        else successful(None)
    }

    val mockAPIDefinitionService: APIDefinitionService = mock[APIDefinitionService]
    val mockDeleteApisJobConfig: DeleteApisJobConfig = mock[DeleteApisJobConfig]
    val underTest = new DeleteApisJob(mockLockKeeper, mockAPIDefinitionService, mockDeleteApisJobConfig)
  }

  "delete apis job execution" should {
    "delete the APIs" in new Setup {
      val serviceNameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(mockAPIDefinitionService.delete(serviceNameCaptor.capture())(any())).thenReturn(successful(()))

      val result: underTest.Result = await(underTest.execute)

      verify(mockAPIDefinitionService, times(ApisToDelete.size)).delete(any())(any())
      result.message shouldBe "DeleteApisJob Job ran successfully."
      serviceNameCaptor.getAllValues should contain theSameElementsAs ApisToDelete
    }

    "not stop when an API fails" in new Setup {
      val serviceNameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(mockAPIDefinitionService.delete(any())(any())).thenReturn(successful(()))
      when(mockAPIDefinitionService.delete(isEq(ApisToDelete.head))(any())).thenReturn(failed(new UnauthorizedException("API has subscribers")))

      val result: underTest.Result = await(underTest.execute)

      verify(mockAPIDefinitionService, times(ApisToDelete.size)).delete(any())(any())
      result.message shouldBe "DeleteApisJob Job ran successfully."
    }

    "not execute if the job is already running" in new Setup {
      override val lockKeeperSuccess: () => Boolean = () => false

      val result: underTest.Result = await(underTest.execute)

      verify(mockAPIDefinitionService, never).delete(any())(any())
      result.message shouldBe "DeleteApisJob did not run because repository was locked by another instance of the scheduler."
    }
  }

}
