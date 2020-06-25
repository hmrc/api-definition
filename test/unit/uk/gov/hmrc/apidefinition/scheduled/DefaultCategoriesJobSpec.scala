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

import java.util.concurrent.TimeUnit.{HOURS, SECONDS}

import org.joda.time.Duration
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.models.APICategory.{LIFETIME_ISA, OTHER}
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.scheduled.{DefaultCategoriesJob, DefaultCategoriesJobConfig, DefaultCategoriesJobLockKeeper}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class DefaultCategoriesJobSpec extends UnitSpec with MockitoSugar with MongoSpecSupport {

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private def anAPIDefinition(name: String) =
    APIDefinition("service", "http://service", name, "description", "context", Seq(), None, None, None)

  trait Setup {
    val mockApiDefinitionRepository = mock[APIDefinitionRepository]
    val lockKeeperSuccess: () => Boolean = () => true

    val mockLockKeeper = new DefaultCategoriesJobLockKeeper(reactiveMongoComponent) {
      //noinspection ScalaStyle
      override def lockId: String = null
      //noinspection ScalaStyle
      override def repo: LockRepository = null
      override val forceLockReleaseAfter: Duration = Duration.standardMinutes(5) // scalastyle:off magic.number
      override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        if (lockKeeperSuccess()) body.map(value => Some(value))
        else Future.successful(None)
    }

    val initialDelay = FiniteDuration(60, SECONDS) // scalastyle:off magic.number
    val interval = FiniteDuration(24, HOURS) // scalastyle:off magic.number
    val config = DefaultCategoriesJobConfig(initialDelay, interval, enabled = true)

    val underTest = new DefaultCategoriesJob(mockLockKeeper, mockApiDefinitionRepository, config)
  }

  "default categories job execution" should {
    import scala.concurrent.ExecutionContext.Implicits.global

    "default categories using the categories map" in new Setup {
      val definition = anAPIDefinition("Lifetime ISA")
      when(mockApiDefinitionRepository.fetchAllWithMissingCategories).thenReturn(successful(Seq(definition)))
      when(mockApiDefinitionRepository.updateCategories(any(), any())).thenReturn(successful(Some(definition)))

      val result = await(underTest.execute)

      result.message shouldBe "DefaultCategoriesJob Job ran successfully."
      verify(mockApiDefinitionRepository, times(1)).updateCategories(definition.context,  Seq(LIFETIME_ISA))
    }

    "default categories to OTHER when API not in the categories map" in new Setup {
      val definition = anAPIDefinition("some other API")
      when(mockApiDefinitionRepository.fetchAllWithMissingCategories).thenReturn(successful(Seq(definition)))
      when(mockApiDefinitionRepository.updateCategories(any(), any())).thenReturn(successful(Some(definition)))

      val result = await(underTest.execute)

      result.message shouldBe "DefaultCategoriesJob Job ran successfully."
      verify(mockApiDefinitionRepository, times(1)).updateCategories(definition.context,  Seq(OTHER))
    }

    "not execute if the job is already running" in new Setup {
      override val lockKeeperSuccess: () => Boolean = () => false

      val result = await(underTest.execute)

      result.message shouldBe "DefaultCategoriesJob did not run because repository was locked by another instance of the scheduler."
    }

    "handle error" in new Setup {
      when(mockApiDefinitionRepository.fetchAllWithMissingCategories)
        .thenReturn(failed(new RuntimeException("A failure on executing updateContext DB operation")))

      val result = await(underTest.execute)

      result.message shouldBe
        "The execution of scheduled job DefaultCategoriesJob failed with error 'A failure on executing updateContext DB operation'." +
          " The next execution of the job will do retry."
    }
  }
}
