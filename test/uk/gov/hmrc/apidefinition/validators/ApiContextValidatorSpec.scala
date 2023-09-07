/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.validators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import cats.data.Validated.{Invalid, Valid}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiContextValidatorSpec extends AsyncHmrcSpec {

  trait Setup {

    def testAPIDefinition(serviceName: String = "money-service", context: ApiContext = ApiContext("money"), versions: List[String] = List("1.0")) =
      ApiDefinition(
        serviceName = serviceName,
        serviceBaseUrl = "http://www.money.com",
        name = "Money API",
        description = "API for checking payments",
        context = context,
        versions = generateApiVersions(versions),
        requiresTrust = Some(false)
      )

    private def generateApiVersions(versions: List[String]): List[ApiVersion] = {
      versions.map(version => {
        ApiVersion(
          ApiVersionNbr(version),
          ApiStatus.PROTOTYPED,
          Some(ApiAccess.PUBLIC),
          List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED))
        )
      })
    }

    def fetchByContextWillReturn(context: ApiContext, apiDefinitionToReturn: Option[ApiDefinition]) =
      when(mockAPIDefinitionService.fetchByContext(context)).thenReturn(successful(apiDefinitionToReturn))

    def fetchByServiceNameWillReturn(serviceName: String, apiDefinitionToReturn: Option[ApiDefinition]) =
      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(apiDefinitionToReturn))

    def verifyValidationPassed(result: validatorUnderTest.HMRCValidated[ApiContext], expectedContext: ApiContext) = {
      result.isValid should be(true)
      val Valid(validatedContext) = result
      validatedContext should be(expectedContext)
    }

    def verifyValidationFailed(result: validatorUnderTest.HMRCValidated[ApiContext], expectedErrors: Seq[String]) = {
      val Invalid(errors) = result
      errors.size should be(expectedErrors.size)
      errors.toList should contain allElementsOf expectedErrors
    }

    def fetchByTopLevelContextWillReturn(apiDefinitions: List[ApiDefinition]) =
      when(mockAPIDefinitionRepository.fetchAllByTopLevelContext(*[ApiContext])).thenReturn(successful(apiDefinitions))

    def thereAreNoOverlappingAPIContexts = fetchByTopLevelContextWillReturn(Nil)

    def contextMustNotBeChangedErrorMessage(errorContext: String): String = s"Field 'context' must not be changed $errorContext"

    val mockAPIDefinitionService: APIDefinitionService       = mock[APIDefinitionService]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockAppConfig: AppConfig                             = mock[AppConfig]
    when(mockAppConfig.skipContextValidationAllowlist).thenReturn(Nil)

    val validatorUnderTest: ApiContextValidator = new ApiContextValidator(mockAPIDefinitionService, mockAPIDefinitionRepository, mockAppConfig)
  }

  "ApiContextValidator" should {
    lazy val errorContext: String = "for API"

    "skip context validation for APIs in the skip context validation allowlist" in new Setup {
      val context: ApiContext               = ApiContext("/totally//inv@lid/context!!")
      val allowedApi                        = "some-important-api"
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(serviceName = allowedApi, context = context)
      when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List(allowedApi))

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for new API with legitimate context" in new Setup {
      lazy val context: ApiContext          = ApiContext("individuals/money")
      lazy val serviceName: String          = "money-service"
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(serviceName, context)

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for existing API with legitimate context" in new Setup {
      lazy val context: ApiContext          = ApiContext("money")
      lazy val serviceName: String          = "money-service"
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(serviceName, context)

      fetchByContextWillReturn(context, Some(apiDefinition))
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for new version of existing API with legacy context" in new Setup {
      lazy val context: ApiContext                        = ApiContext("money")
      lazy val serviceName: String                        = "money-service"
      lazy val apiDefinition: ApiDefinition               = testAPIDefinition(serviceName, context, List("1.0", "2.0"))
      lazy val apiDefinitionWithNewVersion: ApiDefinition = testAPIDefinition(serviceName, context, List("1.0", "2.0", "3.0"))

      fetchByTopLevelContextWillReturn(List(apiDefinition))
      fetchByContextWillReturn(context, Some(apiDefinition))
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinitionWithNewVersion)(context))

      verifyValidationPassed(result, context)
    }

    "fail if new version of existing API has different context" in new Setup {
      lazy val oldContext: ApiContext                     = ApiContext("money")
      lazy val newContext: ApiContext                     = ApiContext("new-money")
      lazy val serviceName: String                        = "money-service"
      lazy val apiDefinition: ApiDefinition               = testAPIDefinition(serviceName, oldContext, List("1.0", "2.0"))
      lazy val apiDefinitionWithNewVersion: ApiDefinition = testAPIDefinition(serviceName, newContext, List("1.0", "2.0", "3.0"))

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinitionWithNewVersion)(newContext))

      val Invalid(errors) = result
      errors.head == contextMustNotBeChangedErrorMessage(errorContext) || errors.tail.contains(contextMustNotBeChangedErrorMessage(errorContext)) shouldBe true
    }

    "fail if context is empty" in new Setup {
      lazy val context: ApiContext          = ApiContext("")
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not be empty $errorContext"))
    }

    "fail validation when the context starts with '/' " in new Setup {
      lazy val context: ApiContext          = ApiContext("/hi")
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not start with '/' $errorContext"))
    }

    "fail validation when the context ends with '/' " in new Setup {
      lazy val context: ApiContext          = ApiContext("hi/")
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not end with '/' $errorContext"))
    }

    "fail validation when the context contains '//' " in new Setup {
      val context: ApiContext               = ApiContext("hi//aloha")
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not have empty path segments $errorContext"))
    }

    val prohibitedChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
    )
    ('{' :: '}' :: prohibitedChars).foreach { char: Char =>
      s"fail validation if the API contains '$char' in the context" in new Setup {
        lazy val badContext                   = ApiContext(s"my-context_$char")
        lazy val apiDefinition: ApiDefinition = testAPIDefinition(context = badContext)

        val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(badContext))

        verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
        verifyValidationFailed(result, Seq(s"Field 'context' should match regular expression '^[a-zA-Z0-9_\\-\\/]+$$' $errorContext"))
      }
    }

    "fail validation when context has been changed" in new Setup {
      lazy val context: ApiContext        = ApiContext("individuals/money")
      lazy val oldContext: ApiContext     = ApiContext("individuals/old-money")
      lazy val serviceName: String        = "money-service"
      val apiDefinition: ApiDefinition    = testAPIDefinition(serviceName, context)
      val oldAPIDefinition: ApiDefinition = testAPIDefinition(serviceName, oldContext)

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, Some(oldAPIDefinition))

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must not be changed $errorContext"))
    }

    "fail validation when context already exist for another API" in new Setup {
      lazy val context: ApiContext      = ApiContext("money")
      lazy val serviceName: String      = "money-service"
      lazy val otherServiceName: String = "other-service"

      val apiDefinition: ApiDefinition      = testAPIDefinition(serviceName, context)
      val otherAPIDefinition: ApiDefinition = testAPIDefinition(otherServiceName, context)

      fetchByContextWillReturn(context, Some(otherAPIDefinition))
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must be unique $errorContext"))
    }

    "accumulate multiple validation errors together" in new Setup {
      lazy val context: ApiContext          = ApiContext("/hi//there/")
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)

      verifyValidationFailed(
        result,
        Seq(
          s"Field 'context' should not start with '/' $errorContext",
          s"Field 'context' should not end with '/' $errorContext",
          s"Field 'context' should not have empty path segments $errorContext"
        )
      )
    }

    val permittedTopLevelContexts = List("accounts", "agents", "customs", "mobile", "individuals", "obligations", "organisations", "test", "payments", "misc")
    permittedTopLevelContexts.foreach(topLevelContext =>
      s"pass validation with a top level context of $topLevelContext" in new Setup {
        lazy val context: ApiContext          = ApiContext(s"$topLevelContext/foo")
        lazy val serviceName: String          = "money-service"
        lazy val apiDefinition: ApiDefinition = testAPIDefinition(serviceName, context)

        thereAreNoOverlappingAPIContexts
        fetchByContextWillReturn(context, None)
        fetchByServiceNameWillReturn(serviceName, None)

        val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

        verifyValidationPassed(result, context)
      }
    )

    "fail validation when new API does not use correct top level context" in new Setup {
      lazy val context: ApiContext          = ApiContext("foo/bar")
      lazy val serviceName: String          = "money-service"
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(serviceName, context)

      val formattedTopLevelContexts: String = permittedTopLevelContexts.sorted.mkString("'", "', '", "'")

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must start with one of $formattedTopLevelContexts $errorContext"))
    }

    "fail validation when a new API does not have a multilevel context" in new Setup {
      lazy val context: ApiContext          = ApiContext(permittedTopLevelContexts.head)
      lazy val serviceName: String          = "money-service"
      lazy val apiDefinition: ApiDefinition = testAPIDefinition(serviceName, context)

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must have at least two segments $errorContext"))
    }

    "fail validation when new API context is subset of existing" in new Setup {
      lazy val supersetContext: ApiContext = ApiContext("individuals/foo/bar")
      lazy val newContext: ApiContext      = ApiContext("individuals/foo")
      lazy val existingAPI: ApiDefinition  = testAPIDefinition("existing-service", supersetContext)
      lazy val newAPI: ApiDefinition       = testAPIDefinition("new-service", newContext)

      fetchByTopLevelContextWillReturn(List(existingAPI))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationFailed(result, Seq(s"Field 'context' overlaps with '$supersetContext' $errorContext"))
    }

    "fail validation when new API context is superset of existing" in new Setup {
      lazy val subsetContext: ApiContext  = ApiContext("individuals/foo")
      lazy val newContext: ApiContext     = ApiContext("individuals/foo/bar")
      lazy val existingAPI: ApiDefinition = testAPIDefinition("existing-service", subsetContext)
      lazy val newAPI: ApiDefinition      = testAPIDefinition("new-service", newContext)

      fetchByTopLevelContextWillReturn(List(existingAPI))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationFailed(result, Seq(s"Field 'context' overlaps with '${subsetContext}' $errorContext"))
    }

    "pass validation when new API context does not overlap multiple other APIs in same top level context" in new Setup {
      lazy val newContext: ApiContext      = ApiContext("individuals/baz")
      lazy val newAPI: ApiDefinition       = testAPIDefinition("new-service", newContext)
      lazy val existingAPI1: ApiDefinition = testAPIDefinition("existing-service-1", ApiContext("individuals/foo"))
      lazy val existingAPI2: ApiDefinition = testAPIDefinition("existing-service-2", ApiContext("individuals/bar"))

      fetchByTopLevelContextWillReturn(List(existingAPI1, existingAPI2))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationPassed(result, newContext)
    }

    "pass validation when new API context string overlaps but does not path overlap" in new Setup {
      lazy val newContext: ApiContext      = ApiContext("individuals/baz")
      lazy val newAPI: ApiDefinition       = testAPIDefinition("new-service", newContext)
      lazy val existingAPI1: ApiDefinition = testAPIDefinition("existing-service-1", ApiContext("individuals/ba"))
      lazy val existingAPI2: ApiDefinition = testAPIDefinition("existing-service-2", ApiContext("individuals/baz2"))

      fetchByTopLevelContextWillReturn(List(existingAPI1, existingAPI2))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[ApiContext] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationPassed(result, newContext)
    }
  }
}
