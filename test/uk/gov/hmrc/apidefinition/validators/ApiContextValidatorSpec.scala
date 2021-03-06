/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.data.Validated.{Invalid, Valid}
import uk.gov.hmrc.apidefinition.config.AppConfig
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiContextValidatorSpec extends AsyncHmrcSpec {

  trait Setup {
    def testAPIDefinition(serviceName: String = "money-service", context: String = "money", versions: Seq[String] = Seq("1.0")) =
      APIDefinition(
        serviceName = serviceName,
        serviceBaseUrl = "http://www.money.com",
        name = "Money API",
        description = "API for checking payments",
        context = context,
        versions = generateApiVersions(versions),
        requiresTrust = Some(false))

    private def generateApiVersions(versions: Seq[String]): Seq[APIVersion] = {
      versions.map(version => {
        APIVersion(
          version,
          APIStatus.PROTOTYPED,
          Some(PublicAPIAccess()),
          Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))
      })
    }

    def fetchByContextWillReturn(context: String, apiDefinitionToReturn: Option[APIDefinition]) =
      when(mockAPIDefinitionService.fetchByContext(context)).thenReturn(successful(apiDefinitionToReturn))

    def fetchByServiceNameWillReturn(serviceName: String, apiDefinitionToReturn: Option[APIDefinition]) =
      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(apiDefinitionToReturn))

    def verifyValidationPassed(result: validatorUnderTest.HMRCValidated[String], expectedContext: String) = {
      result.isValid should be(true)
      val Valid(validatedContext) = result
      validatedContext should be(expectedContext)
    }

    def verifyValidationFailed(result: validatorUnderTest.HMRCValidated[String], expectedErrors: Seq[String]) = {
      val Invalid(errors) = result
      errors.size should be(expectedErrors.size)
      errors.toList should contain allElementsOf expectedErrors
    }

    def fetchByTopLevelContextWillReturn(apiDefinitions: Seq[APIDefinition]) =
      when(mockAPIDefinitionRepository.fetchAllByTopLevelContext(any[String])).thenReturn(successful(apiDefinitions))

    def thereAreNoOverlappingAPIContexts = fetchByTopLevelContextWillReturn(Seq.empty)

    def contextMustNotBeChangedErrorMessage(errorContext: String): String = s"Field 'context' must not be changed $errorContext"

    val mockAPIDefinitionService: APIDefinitionService = mock[APIDefinitionService]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
    val mockAppConfig: AppConfig = mock[AppConfig]
    when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List())

    val validatorUnderTest: ApiContextValidator = new ApiContextValidator(mockAPIDefinitionService, mockAPIDefinitionRepository, mockAppConfig)
  }

  "ApiContextValidator" should {
    lazy val errorContext: String = "for API"

    "skip context validation for APIs in the skip context validation allowlist" in new Setup {
      val context: String = "/totally//inv@lid/context!!"
      val allowedApi = "some-important-api"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName = allowedApi, context = context)
      when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List(allowedApi))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for new API with legitimate context" in new Setup {
      lazy val context: String = "individuals/money"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for existing API with legitimate context" in new Setup {
      lazy val context: String = "money"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      fetchByContextWillReturn(context, Some(apiDefinition))
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for new version of existing API with legacy context" in new Setup {
      lazy val context: String = "money"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context, Seq("1.0", "2.0"))
      lazy val apiDefinitionWithNewVersion: APIDefinition = testAPIDefinition(serviceName, context, Seq("1.0", "2.0", "3.0"))

      fetchByTopLevelContextWillReturn(Seq(apiDefinition))
      fetchByContextWillReturn(context, Some(apiDefinition))
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinitionWithNewVersion)(context))

      verifyValidationPassed(result, context)
    }

    "fail if new version of existing API has different context" in new Setup {
      lazy val oldContext: String = "money"
      lazy val newContext = "new-money"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, oldContext, Seq("1.0", "2.0"))
      lazy val apiDefinitionWithNewVersion: APIDefinition = testAPIDefinition(serviceName, newContext, Seq("1.0", "2.0", "3.0"))

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinitionWithNewVersion)(newContext))

      val Invalid(errors) = result
      errors.head == contextMustNotBeChangedErrorMessage(errorContext) || errors.tail.contains(contextMustNotBeChangedErrorMessage(errorContext)) shouldBe true
    }

    "fail if context is empty" in new Setup {
      lazy val context: String = ""
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not be empty $errorContext"))
    }

    "fail validation when the context starts with '/' " in new Setup {
      lazy val context: String = "/hi"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not start with '/' $errorContext"))
    }

    "fail validation when the context ends with '/' " in new Setup {
      lazy val context: String = "hi/"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not end with '/' $errorContext"))
    }

    "fail validation when the context contains '//' " in new Setup {
      val context: String = "hi//aloha"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not have empty path segments $errorContext"))
    }

    val prohibitedChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
    )
    ('{' :: '}' :: prohibitedChars).foreach { char: Char =>
      s"fail validation if the API contains '$char' in the context" in new Setup {
        lazy val badContext = s"my-context_$char"
        lazy val apiDefinition: APIDefinition = testAPIDefinition(context = badContext)

        val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(badContext))

        verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
        verifyValidationFailed(result, Seq(s"Field 'context' should match regular expression '^[a-zA-Z0-9_\\-\\/]+$$' $errorContext"))
      }
    }

    "fail validation when context has been changed" in new Setup {
      lazy val context: String = "individuals/money"
      lazy val oldContext: String = "individuals/old-money"
      lazy val serviceName: String = "money-service"
      val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)
      val oldAPIDefinition: APIDefinition = testAPIDefinition(serviceName, oldContext)

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, Some(oldAPIDefinition))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must not be changed $errorContext"))
    }

    "fail validation when context already exist for another API" in new Setup {
      lazy val context: String = "money"
      lazy val serviceName: String = "money-service"
      lazy val otherServiceName: String = "other-service"

      val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)
      val otherAPIDefinition: APIDefinition = testAPIDefinition(otherServiceName, context)

      fetchByContextWillReturn(context, Some(otherAPIDefinition))
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must be unique $errorContext"))
    }

    "accumulate multiple validation errors together" in new Setup {
      lazy val context: String = "/hi//there/"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)

      verifyValidationFailed(
        result,
        Seq(
          s"Field 'context' should not start with '/' $errorContext",
          s"Field 'context' should not end with '/' $errorContext",
          s"Field 'context' should not have empty path segments $errorContext"))
    }

    val permittedTopLevelContexts = List("accounts", "agents", "customs", "mobile", "individuals", "obligations", "organisations", "test", "payments", "misc")
    permittedTopLevelContexts.foreach(topLevelContext =>
      s"pass validation with a top level context of $topLevelContext" in new Setup {
        lazy val context: String = s"$topLevelContext/foo"
        lazy val serviceName: String = "money-service"
        lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

        thereAreNoOverlappingAPIContexts
        fetchByContextWillReturn(context, None)
        fetchByServiceNameWillReturn(serviceName, None)

        val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

        verifyValidationPassed(result, context)
      }
    )

    "fail validation when new API does not use correct top level context" in new Setup {
      lazy val context: String = "foo/bar"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      val formattedTopLevelContexts: String = permittedTopLevelContexts.sorted.mkString("'", "', '", "'")

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must start with one of $formattedTopLevelContexts $errorContext"))
    }

    "fail validation when a new API does not have a multilevel context" in new Setup {
      lazy val context: String = permittedTopLevelContexts.head
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      thereAreNoOverlappingAPIContexts
      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must have at least two segments $errorContext"))
    }

    "fail validation when new API context is subset of existing" in new Setup {
      lazy val newContext: String = "individuals/foo"
      lazy val supersetContext: String = s"$newContext/bar"
      lazy val existingAPI: APIDefinition = testAPIDefinition("existing-service", supersetContext)
      lazy val newAPI: APIDefinition = testAPIDefinition("new-service", newContext)

      fetchByTopLevelContextWillReturn(Seq(existingAPI))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationFailed(result, Seq(s"Field 'context' overlaps with '$supersetContext' $errorContext"))
    }

    "fail validation when new API context is superset of existing" in new Setup {
      lazy val subsetContext: String = "individuals/foo"
      lazy val newContext: String = s"$subsetContext/bar"
      lazy val existingAPI: APIDefinition = testAPIDefinition("existing-service", subsetContext)
      lazy val newAPI: APIDefinition = testAPIDefinition("new-service", newContext)

      fetchByTopLevelContextWillReturn(Seq(existingAPI))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationFailed(result, Seq(s"Field 'context' overlaps with '$subsetContext' $errorContext"))
    }

    "pass validation when new API context does not overlap multiple other APIs in same top level context" in new Setup {
      lazy val newContext: String = "individuals/baz"
      lazy val newAPI: APIDefinition = testAPIDefinition("new-service", newContext)
      lazy val existingAPI1: APIDefinition = testAPIDefinition("existing-service-1", "individuals/foo")
      lazy val existingAPI2: APIDefinition = testAPIDefinition("existing-service-2", "individuals/bar")

      fetchByTopLevelContextWillReturn(Seq(existingAPI1, existingAPI2))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationPassed(result, newContext)
    }

    "pass validation when new API context string overlaps but does not path overlap" in new Setup {
      lazy val newContext: String = "individuals/baz"
      lazy val newAPI: APIDefinition = testAPIDefinition("new-service", newContext)
      lazy val existingAPI1: APIDefinition = testAPIDefinition("existing-service-1", "individuals/ba")
      lazy val existingAPI2: APIDefinition = testAPIDefinition("existing-service-2", "individuals/baz2")

      fetchByTopLevelContextWillReturn(Seq(existingAPI1, existingAPI2))
      fetchByContextWillReturn(newContext, None)
      fetchByServiceNameWillReturn("new-service", None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, newAPI)(newContext))

      verifyValidationPassed(result, newContext)
    }
  }
}
