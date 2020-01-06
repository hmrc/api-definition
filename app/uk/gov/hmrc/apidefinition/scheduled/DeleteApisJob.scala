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

import javax.inject.Inject
import org.joda.time.Duration
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.apidefinition.scheduled.DeleteApisJob.ApisToDelete
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import scala.concurrent.Future.sequence
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object DeleteApisJob {
  val ApisToDelete: Seq[String] = Seq("api-scope", "api-subscription-fields", "api-publisher", "api-definition",
    "third-party-application", "third-party-developer")
}

class DeleteApisJob @Inject()(override val lockKeeper: DeleteApisJobLockKeeper,
                              apiDefinitionService: APIDefinitionService,
                              jobConfig: DeleteApisJobConfig) extends ScheduledMongoJob {

  override def name: String = "DeleteApisJob"

  override def interval: FiniteDuration = jobConfig.interval

  override def initialDelay: FiniteDuration = jobConfig.initialDelay

  override def runJob(implicit ec: ExecutionContext): Future[RunningOfJobSuccessful] = {
    Logger.info("Starting DeleteApisJob")
    sequence {
      deleteApis()
    } map { _ =>
      Logger.info("Finished DeleteApisJob")
      RunningOfJobSuccessful
    }
  }

  private def deleteApis()(implicit ec: ExecutionContext): Seq[Future[Unit]] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    ApisToDelete map { serviceName =>
      apiDefinitionService.delete(serviceName) map { _ =>
        Logger.info(s"API $serviceName has been deleted")
      } recover {
        case NonFatal(e) => Logger.error(s"Could not delete API $serviceName", e)
      }
    }
  }
}

class DeleteApisJobLockKeeper @Inject()(mongo: ReactiveMongoComponent) extends LockKeeper {
  override def repo: LockRepository = new LockRepository()(mongo.mongoConnector.db)

  override def lockId: String = "DeleteApisJob"

  override val forceLockReleaseAfter: Duration = Duration.standardHours(2)

}

case class DeleteApisJobConfig(initialDelay: FiniteDuration, interval: FiniteDuration, enabled: Boolean)
