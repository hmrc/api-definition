/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.mocks

import scala.concurrent.Future.{failed, successful}

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ServiceName, StoredApiDefinition}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository

trait ApiDefinitionRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseApiDefinitionRepositoryMock {
    def aMock: APIDefinitionRepository

    object FetchByServiceName {

      def returns(apiDefinition: StoredApiDefinition) = {
        when(aMock.fetchByServiceName(*)).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNone() = {
        when(aMock.fetchByServiceName(*)).thenReturn(successful(None))
      }

      def returnsFor(serviceName: ServiceName)(apiDefinition: StoredApiDefinition) = {
        when(aMock.fetchByServiceName(eqTo(serviceName))).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNoneFor(serviceName: ServiceName) = {
        when(aMock.fetchByServiceName(eqTo(serviceName))).thenReturn(successful(None))
      }

      def failsFor(serviceName: ServiceName)(exception: RuntimeException) = {
        when(aMock.fetchByServiceName(eqTo(serviceName))).thenReturn(failed(exception))
      }

      def verifyCalledFor(serviceName: ServiceName, verifcationMode: VerificationMode = times(1)) = {
        verify(aMock, verifcationMode).fetchByServiceName(eqTo(serviceName))
      }
    }

    object FetchAll {

      def returns(apiDefinitions: StoredApiDefinition*) = {
        when(aMock.fetchAll()).thenReturn(successful(apiDefinitions.toSeq))
      }
    }

    object Save {

      def returns(out: StoredApiDefinition) = {
        when(aMock.save(*)).thenReturn(successful(out))
      }

      def returnsFor(in: StoredApiDefinition)(out: StoredApiDefinition) = {
        when(aMock.save(in)).thenReturn(successful(out))
      }

      def failsFor(in: StoredApiDefinition)(exception: RuntimeException) = {
        when(aMock.save(in)).thenReturn(failed(exception))
      }

      def verifySaved(record: StoredApiDefinition, verifcationMode: VerificationMode = times(1)) = {
        verify(aMock, verifcationMode).save(record)
      }
    }

    object Delete {

      def suceedsFor(serviceName: ServiceName) = {
        when(aMock.delete(eqTo(serviceName))).thenReturn(successful(()))
      }

      def failsFor(serviceName: ServiceName) = {
        when(aMock.delete(eqTo(serviceName))).thenReturn(failed(new RuntimeException()))
      }

      def verifyDeletedBy(serviceName: ServiceName) = {
        verify(aMock, times(1)).delete(eqTo(serviceName))
      }
    }

    object FetchByContext {

      def returnsFor(context: ApiContext)(apiDefinition: StoredApiDefinition) = {
        when(aMock.fetchByContext(context)).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNoneFor(context: ApiContext) = {
        when(aMock.fetchByContext(context)).thenReturn(successful(None))
      }
    }

    object FetchByServiceBaseUrl {

      def returnsFor(serviceBaseUrl: String)(apiDefinition: StoredApiDefinition) = {
        when(aMock.fetchByServiceBaseUrl(serviceBaseUrl)).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNoneFor(serviceBaseUrl: String) = {
        when(aMock.fetchByServiceBaseUrl(serviceBaseUrl)).thenReturn(successful(None))
      }
    }

    object FetchByName {

      def returnsFor(name: String)(apiDefinition: StoredApiDefinition) = {
        when(aMock.fetchByName(name)).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNoneFor(name: String) = {
        when(aMock.fetchByName(name)).thenReturn(successful(None))
      }
    }

    object FetchAllByTopLevelContext {

      def returnsFor(topLevelContext: ApiContext)(apiDefinitions: StoredApiDefinition*) = {
        when(aMock.fetchAllByTopLevelContext(topLevelContext)).thenReturn(successful(apiDefinitions))
      }

      def returnsNilFor(topLevelContext: ApiContext) = {
        when(aMock.fetchAllByTopLevelContext(topLevelContext)).thenReturn(successful(Nil))
      }
    }
  }

  object ApiDefinitionRepositoryMock extends BaseApiDefinitionRepositoryMock {
    val aMock = mock[APIDefinitionRepository]
  }
}
