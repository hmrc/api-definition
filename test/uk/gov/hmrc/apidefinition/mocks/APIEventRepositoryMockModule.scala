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

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apidefinition.repository.APIEventRepository

trait APIEventRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseAPIEventRepositoryMock {
    def aMock: APIEventRepository

    object CreateAll {

      def success() = {
        when(aMock.createAll(*)).thenReturn(successful(true))
      }

    }
  }

  object APIEventRepositoryMock extends BaseAPIEventRepositoryMock {
    val aMock = mock[APIEventRepository]

  }
}
