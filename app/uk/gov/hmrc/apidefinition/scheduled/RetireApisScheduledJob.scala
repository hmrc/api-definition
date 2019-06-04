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

package uk.gov.hmrc.apidefinition.scheduled

import javax.inject.Inject
import org.joda.time.Duration
import play.api.Logger
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.models.APIStatus.RETIRED
import uk.gov.hmrc.apidefinition.models.{APIDefinition, APIVersion}
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import scala.concurrent.Future.{failed, sequence}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class RetireApisScheduledJob @Inject()(override val lockKeeper: RetireApisJobLockKeeper,
                                       jobConfig: RetireApisJobConfig,
                                       apiDefinitionRepository: APIDefinitionRepository) extends ScheduledMongoJob {

  override def name: String = "RetireApisScheduledJob"

  override def interval: FiniteDuration = jobConfig.interval

  override def initialDelay: FiniteDuration = jobConfig.initialDelay

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    Logger.info("Starting RetireApisScheduledJob")

    (for {
      apiDefinitions <- apiDefinitionRepository.find("serviceBaseUrl" -> Json.obj("$regex" -> "service$"))
      retiredApiDefinitions = apiDefinitions.map(api => retire(api))
      _ <- sequence(retiredApiDefinitions.map(api => apiDefinitionRepository.save(api)))
    } yield RunningOfJobSuccessful) recoverWith {
      case e: Throwable =>
        Logger.error("Could not retire APIs", e)
        failed(RunningOfJobFailed(name, e))
    }
  }

  private def retire(apiDefinition: APIDefinition): APIDefinition = {
    Logger.info(s"Retiring API ${apiDefinition.serviceName}")
    val retiredVersions: Seq[APIVersion] = apiDefinition.versions.map(v => v.copy(status = RETIRED))
    apiDefinition.copy(versions = retiredVersions)
  }
}

class RetireApisJobLockKeeper @Inject()(mongo: ReactiveMongoComponent) extends LockKeeper {
  override def repo: LockRepository = new LockRepository()(mongo.mongoConnector.db)

  override def lockId: String = "RetireApisScheduledJob"

  override val forceLockReleaseAfter: Duration = Duration.standardHours(2)

}

case class RetireApisJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean)
