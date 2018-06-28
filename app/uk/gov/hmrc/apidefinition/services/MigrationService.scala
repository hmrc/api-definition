/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.services

import javax.inject.{Inject, Singleton}
import org.joda.time.Duration._
import play.api.Logger
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository}
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.utils.APIDefinitionMapper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MigrationService @Inject()(val apiDefinitionRepository: APIDefinitionRepository,
                                 val apiDefinitionMapper: APIDefinitionMapper) extends MongoDbConnection {

  val lockKeeper = new LockKeeper {
    lazy val mongo: () => DB = mongoConnector.db
    override def repo = LockMongoRepository(mongo)
    override def lockId = "migrate-api-definition"
    override val forceLockReleaseAfter = standardMinutes(2)
  }

  def migrate(): Future[Unit] = {

    def migrateApiDefinition(apiDefinition: APIDefinition) = {
      val migratedApiDefinition = apiDefinitionMapper.mapLegacyStatuses(apiDefinition)
      if (migratedApiDefinition != apiDefinition) {
        apiDefinitionRepository.save(migratedApiDefinition).map { _ =>
          Logger.info(s"Migrated legacy statuses on API definition for context=${apiDefinition.context}")
        }
      } else Future.successful(())
    }

    lockKeeper.tryLock {
      Logger.info("Starting migration of legacy statuses on API definitions")
      for {
        apiDefinitions <- apiDefinitionRepository.fetchAll()
        _ <- Future.sequence(apiDefinitions.map(migrateApiDefinition))
      } yield ()
    } map {
      case None => Logger.warn("Migrations skipped due to unobtainable database lock")
      case _ => ()
    }
  }

}
