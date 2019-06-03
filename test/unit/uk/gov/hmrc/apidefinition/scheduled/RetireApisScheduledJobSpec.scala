/*
 * Copyright 2019 HM Revenue & Customs
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

import java.util.concurrent.TimeUnit.{DAYS, SECONDS}

import org.joda.time.Duration
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.models.APIStatus.{PROTOTYPED, RETIRED}
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.scheduled.{RetireApisJobConfig, RetireApisJobLockKeeper, RetireApisScheduledJob}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class RetireApisScheduledJobSpec extends UnitSpec with MockitoSugar {

  private val apiVersion = APIVersion(
    version = "1.0",
    status = PROTOTYPED,
    access = None,
    endpoints = Seq(Endpoint("/world", "Say Hello to the World!", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
  )

  private val apiDefinition = APIDefinition(
    serviceName = "hello-service",
    serviceBaseUrl = "hello.service",
    name = "Hello",
    description = "This is the Hello API",
    context = "hello",
    versions = Seq(apiVersion),
    requiresTrust = None)

  trait Setup {
    val mockApiDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mongo: ReactiveMongoComponent = mock[ReactiveMongoComponent]

    val lockKeeperSuccess: () => Boolean = () => true

    val mockLockKeeper: RetireApisJobLockKeeper = new RetireApisJobLockKeeper(mongo) {
      override def lockId: String = "testLock"

      override def repo: LockRepository = mock[LockRepository]

      override val forceLockReleaseAfter: Duration = Duration.standardMinutes(5) // scalastyle:off magic.number

      override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        if (lockKeeperSuccess()) body.map(value => successful(Some(value)))
        else successful(None)
    }

    val config = RetireApisJobConfig(FiniteDuration(120, SECONDS), FiniteDuration(60, DAYS), enabled = true) // scalastyle:off magic.number
    val underTest = new RetireApisScheduledJob(mockLockKeeper, config, mockApiDefinitionRepository)

    when(mockApiDefinitionRepository.save(any())).thenAnswer(new Answer[Future[APIDefinition]] {
      override def answer(invocation: InvocationOnMock): Future[APIDefinition] = {
        successful(invocation.getArguments()(0).asInstanceOf[APIDefinition])
      }
    })
  }

  "Retire APIs job execution" should {
    "attempt to retire APIs that match the query" in new Setup {
      when(mockApiDefinitionRepository.find("serviceBaseUrl" -> Json.obj("$regex" -> "service$")))
        .thenReturn(successful(List(apiDefinition)))

      await(underTest.execute)

      verify(mockApiDefinitionRepository, times(1)).find("serviceBaseUrl" -> Json.obj("$regex" -> "service$"))
      verify(mockApiDefinitionRepository, times(1)).save(apiDefinition.copy(versions = Seq(apiVersion.copy(status = RETIRED))))
      verifyNoMoreInteractions(mockApiDefinitionRepository)
    }

    "not execute if the job is already running" in new Setup {
      override val lockKeeperSuccess: () => Boolean = () => false

      await(underTest.execute)

      verifyZeroInteractions(mockApiDefinitionRepository)
    }

    "handle error when fetching APIs fails" in new Setup {
      when(mockApiDefinitionRepository.find("serviceBaseUrl" -> Json.obj("$regex" -> "service$"))).thenReturn(failed(new RuntimeException("Unexpected Error")))

      val result: underTest.Result = await(underTest.execute)

      result.message shouldBe
        "The execution of scheduled job RetireApisScheduledJob failed with error" +
        " 'Unexpected Error'. The next execution of the job will do retry."
    }
  }
}
