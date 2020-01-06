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

package uk.gov.hmrc.apidefinition.config

import com.google.inject.AbstractModule
import javax.inject.{Inject, Singleton}
import play.api.Application
import uk.gov.hmrc.apidefinition.scheduled.{DeleteApisJob, DeleteApisJobConfig}
import uk.gov.hmrc.play.scheduling.{ExclusiveScheduledJob, RunningOfScheduledJobs}

class SchedulerModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Scheduler]).asEagerSingleton()
  }
}

@Singleton
class Scheduler @Inject()(deleteApisJobConfig: DeleteApisJobConfig,
                          deleteApisJob: DeleteApisJob,
                          app: Application) extends RunningOfScheduledJobs {

  override val scheduledJobs: Seq[ExclusiveScheduledJob] = {
    if (deleteApisJobConfig.enabled) {
      Seq(deleteApisJob)
    } else {
      Seq.empty
    }
  }

  onStart(app)
}
