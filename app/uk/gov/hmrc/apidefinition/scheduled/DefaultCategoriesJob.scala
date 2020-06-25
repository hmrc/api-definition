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

package uk.gov.hmrc.apidefinition.scheduled

import com.google.inject.Singleton
import javax.inject.Inject
import org.joda.time.Duration
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.models.APICategory.{APICategory, OTHER, categoryMap}
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import scala.concurrent.Future.sequence
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
@scala.deprecated("this needs removing once it's run in prod")
class DefaultCategoriesJob @Inject()(val lockKeeper: DefaultCategoriesJobLockKeeper,
                                 apiDefinitionRepository: APIDefinitionRepository,
                                 jobConfig: DefaultCategoriesJobConfig) extends ScheduledMongoJob {

  override def name: String = "DefaultCategoriesJob"
  override def interval: FiniteDuration = jobConfig.interval
  override def initialDelay: FiniteDuration = jobConfig.initialDelay
  override val isEnabled: Boolean = jobConfig.enabled

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    (for {
      apiDefinitions <- apiDefinitionRepository.fetchAllWithMissingCategories
      _ = Logger.info(s"Found ${apiDefinitions.size} API definitions without categories")
      _ <- sequence(apiDefinitions.map(updateApiDefinition))
    } yield RunningOfJobSuccessful) recoverWith {
      case e: Throwable => Future.failed(RunningOfJobFailed(name, e))
    }
  }

  private def updateApiDefinition(apiDefinition: APIDefinition): Future[Option[APIDefinition]] = {
    val defaultCategories: Seq[APICategory] = categoryMap.getOrElse(apiDefinition.name, Seq(OTHER))
    apiDefinitionRepository.updateCategories(apiDefinition.context, defaultCategories)
  }
}

class DefaultCategoriesJobLockKeeper @Inject()(mongo: ReactiveMongoComponent) extends LockKeeper {
  override def repo: LockRepository = new LockRepository()(mongo.mongoConnector.db)

  override def lockId: String = "DefaultCategoriesJob"

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(60) // scalastyle:off magic.number
}

case class DefaultCategoriesJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean)
