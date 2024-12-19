/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ServiceName, StoredApiDefinition}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

import uk.gov.hmrc.apidefinition.models.ApiEvent
import uk.gov.hmrc.apidefinition.services.APIDefinitionService

trait ApiDefinitionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseApiDefinitionServiceMock {
    def aMock: APIDefinitionService

    object CreateOrUpdate {

      def success() = {
        when(aMock.createOrUpdate(*)(*)).thenReturn(successful(()))
      }

      def thenFails() = {
        when(aMock.createOrUpdate(*)(*)).thenReturn(failed(new RuntimeException(s"Could not publish API: [TEST]")))
      }

      def verifyCall(apiDefinition: StoredApiDefinition) = {
        verify(aMock).createOrUpdate(eqTo(apiDefinition))(*)
      }

    }

    object Delete {

      def success(serviceName: ServiceName) = {
        when(aMock.delete(eqTo(serviceName))(*)).thenReturn(successful(()))
      }

      def thenFailsWith(exception: Throwable) = {
        when(aMock.delete(*[ServiceName])(*)).thenReturn(failed(exception))
      }

    }

    object FetchByContext {

      def success(apiDefinition: ApiDefinition) = {
        when(aMock.fetchByContext(*[ApiContext])).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNone() = {
        when(aMock.fetchByContext(*[ApiContext])).thenReturn(successful(None))
      }

      def returnsNoneForContext(context: ApiContext) = {
        when(aMock.fetchByContext(eqTo(context))).thenReturn(successful(None))
      }

      def thenFails() = {
        when(aMock.fetchByContext(*[ApiContext])).thenReturn(failed(new RuntimeException(s"Something went wrong")))
      }

      def verifyCalled(context: ApiContext) = {
        verify(aMock).fetchByContext(eqTo(context))
      }
    }

    object FetchAllPublicAPIs {

      def success(apiDefinitions: List[ApiDefinition]) = {
        when(aMock.fetchAllPublicAPIs(*)).thenReturn(successful(apiDefinitions))
      }

      def thenFails(alsoIncludePrivateTrials: Boolean) = {
        when(aMock.fetchAllPublicAPIs(eqTo(alsoIncludePrivateTrials))).thenReturn(failed(new RuntimeException(s"Something went wrong")))
      }

      def verifyCalled(alsoIncludePrivateTrials: Boolean) = {
        verify(aMock).fetchAllPublicAPIs(eqTo(alsoIncludePrivateTrials))
      }
    }

    object FetchAllPrivateAPIs {

      def success(apiDefinitions: List[ApiDefinition]) = {
        when(aMock.fetchAllPrivateAPIs()).thenReturn(successful(apiDefinitions))
      }

      def thenFails() = {
        when(aMock.fetchAllPrivateAPIs()).thenReturn(failed(new RuntimeException(s"Something went wrong")))
      }
    }

    object FetchAll {

      def success(apiDefinitions: List[ApiDefinition]) = {
        when(aMock.fetchAll).thenReturn(successful(apiDefinitions))
      }

      def verifyCalled() = {
        verify(aMock).fetchAll
      }

      def thenFails() = {
        when(aMock.fetchAll).thenReturn(failed(new RuntimeException(s"Something went wrong")))
      }
    }

    object FetchByName {

      def returnsNone() = {
        when(aMock.fetchByName(*)).thenReturn(successful(None))
      }
    }

    object FetchByServiceBaseUrl {

      def returnsNone() = {
        when(aMock.fetchByServiceBaseUrl(*)).thenReturn(successful(None))
      }

    }

    object FetchByServiceName {

      def success(serviceName: ServiceName, apiDefinition: ApiDefinition) = {
        when(aMock.fetchByServiceName(eqTo(serviceName))).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNone() = {
        when(aMock.fetchByServiceName(*[ServiceName])).thenReturn(successful(None))
      }

      def thenFails() = {
        when(aMock.fetchByServiceName(*[ServiceName])).thenReturn(failed(new RuntimeException(s"Something went wrong")))
      }
    }

    object FetchByServiceUrL {

      def returnsNone() = {
        when(aMock.fetchByServiceBaseUrl(*)).thenReturn(successful(None))
      }
    }

    object PublishAllToAws {

      def success() = {
        when(aMock.publishAllToAws()(*)).thenReturn(successful(()))
      }

      def thenFailsWith(message: String) = {
        when(aMock.publishAllToAws()(*)).thenReturn(failed(new RuntimeException(message)))
      }

    }

    object FetchEventsByServiceName {

      def success(serviceName: ServiceName, apiEvents: List[ApiEvent], includeNoChange: Boolean = true) = {
        when(aMock.fetchEventsByServiceName(eqTo(serviceName), eqTo(includeNoChange))).thenReturn(successful(apiEvents))
      }

      def notIncludingNoChangeRequestSuccess(serviceName: ServiceName, apiEvents: List[ApiEvent]) = {
        when(aMock.fetchEventsByServiceName(eqTo(serviceName), eqTo(false))).thenReturn(successful(apiEvents))
      }

      def thenFails() = {
        when(aMock.fetchEventsByServiceName(*[ServiceName], *[Boolean])).thenReturn(failed(new RuntimeException(s"Something went wrong")))
      }
    }

    object DeleteEventsByServiceName {

      def success(serviceName: ServiceName) = {
        when(aMock.deleteEventsByServiceName(eqTo(serviceName))).thenReturn(successful(()))
      }
    }
  }

  object ApiDefinitionServiceMock extends BaseApiDefinitionServiceMock {
    val aMock = mock[APIDefinitionService]
  }
}
