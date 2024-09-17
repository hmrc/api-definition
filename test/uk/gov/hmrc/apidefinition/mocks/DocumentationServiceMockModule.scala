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

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.mvc.Results._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiVersionNbr

import uk.gov.hmrc.apidefinition.services.DocumentationService

trait DocumentationServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseDocumentationServiceMock {
    def documentationMock: DocumentationService

    object FetchApiDocumentationResource {

      def success(body: String, contentType: String = "application/json") = when(documentationMock.fetchApiDocumentationResource(*[ServiceName], *[ApiVersionNbr], *))
        .thenReturn(successful(Ok(body).withHeaders(CONTENT_TYPE -> contentType)))

      def errors(throwable: Throwable) = when(documentationMock.fetchApiDocumentationResource(*[ServiceName], *[ApiVersionNbr], *))
        .thenReturn(failed(throwable))

      def verifyCalled(serviceName: ServiceName, version: ApiVersionNbr, resourceName: String) = {
        verify(documentationMock).fetchApiDocumentationResource(eqTo(serviceName), eqTo(version), eqTo(resourceName))
      }
    }
  }

  object DocumentationServiceMock extends BaseDocumentationServiceMock {
    val documentationMock = mock[DocumentationService]
  }
}
