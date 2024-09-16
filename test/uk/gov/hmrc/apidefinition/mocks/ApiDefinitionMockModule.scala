package uk.gov.hmrc.apidefinition.mocks


import scala.concurrent.Future.{failed, successful}
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, StoredApiDefinition}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApplicationId}


trait ApiDefinitionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseApiDefinitionServiceMock {
    def aMock: APIDefinitionService

    object CreateOrUpdate {

      def success() = {
        when(aMock.createOrUpdate(*)(*)).thenReturn(successful(()))
      }

      def thenFails() ={
        when(aMock.createOrUpdate(*)(*)).thenReturn( failed(new RuntimeException(s"Could not publish API: [TEST]")))
      }

    }

    object FetchByContext {

      def success(apiDefinition: ApiDefinition) ={
        when(aMock.fetchByContext(*[ApiContext])).thenReturn(successful(Some(apiDefinition)))
      }

      def returnsNone() = {
        when(aMock.fetchByContext(*[ApiContext])).thenReturn(successful(None))
      }
      //TODO Failure cases
    }

    object FetchAllPublicAPIs {
      def success(apiDefinitions: List[ApiDefinition]) ={
        when(aMock.fetchAllPublicAPIs(*)).thenReturn(successful(apiDefinitions))
      }

      //TODO Failure cases
    }

    object FetchAllPrivateAPIs {

      def success(apiDefinitions: List[ApiDefinition]) = {
        when(aMock.fetchAllPrivateAPIs()).thenReturn(successful(apiDefinitions))
      }
      //TODO Failure cases
    }

    object FetchAll {
      def success(apiDefinitions: List[ApiDefinition]) = {
        when(aMock.fetchAll).thenReturn(successful(apiDefinitions))
      }
      //TODO Failure cases
    }

    object FetchByName {
      def returnsNone() = {
        when(aMock.fetchByName(*)).thenReturn(successful(None))
      }
    }

    object FetchByServiceUrL{
      def returnsNone() = {
        when(aMock.fetchByServiceBaseUrl(*)).thenReturn(successful(None))
      }
    }
  }



  object ApiDefinitionServiceMock extends BaseApiDefinitionServiceMock {
    val aMock = mock[APIDefinitionService]
  }
}
