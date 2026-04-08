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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{Endpoint, StoredApiDefinition, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}

class ApiDefinitionValidatorSpec extends AbstractValidatorSpec {

  private val calendarVersion = ApiVersion(
    ApiVersionNbr("1.0"),
    ApiStatus.BETA,
    ApiAccess.PUBLIC,
    List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
    endpointsEnabled = true
  )

  private val calendarApi = StoredApiDefinition(
    ServiceName("calendar"),
    "http://calendar",
    "Calendar API",
    "My Calendar API",
    ApiContext("individuals/calendar"),
    List(calendarVersion),
    false,
    categories = List(ApiCategory.OTHER)
  )

  "ApiDefinitionValidator" when {
    "validateOtherFields" should {
      "succeed with a good API definition" in {
        validates(ApiDefinitionValidator.validateOtherFields(calendarApi))
      }

      "fail when the description is empty" in {
        failsToValidate(ApiDefinitionValidator.validateOtherFields(calendarApi.copy(description = "")))("Field 'description' should not be empty")
      }

      "fail when the categories are empty" in {
        failsToValidate(ApiDefinitionValidator.validateOtherFields(calendarApi.copy(categories = List.empty)))("Field 'categories' should not be empty")
      }

      "fail when the versions are empty" in {
        failsToValidate(ApiDefinitionValidator.validateOtherFields(calendarApi.copy(versions = List.empty)))("Field 'versions' should not be empty")
      }

      "fail when the versions are not unique" in {
        failsToValidate(ApiDefinitionValidator.validateOtherFields(calendarApi.copy(versions = calendarApi.versions.appended(calendarApi.versions.head))))(
          "Field 'version' must be unique"
        )
      }

      "collect all empty field failures" in {
        failsToValidate(
          ApiDefinitionValidator.validateOtherFields(
            calendarApi.copy(
              description = "  ",
              categories = List.empty,
              versions = List.empty
            )
          ),
          numberOfErrors = 3
        )(
          "Field 'description' should not be empty",
          "Field 'categories' should not be empty",
          "Field 'versions' should not be empty"
        )
      }

      "collect all version failures" in {
        failsToValidate(
          ApiDefinitionValidator.validateOtherFields(
            calendarApi.copy(versions =
              List(
                calendarVersion.copy(endpoints = List.empty),
                calendarVersion.copy(versionNbr = ApiVersionNbr("2.0"), endpoints = List.empty)
              )
            )
          ),
          numberOfErrors = 2
        )(
          "Version 1.0 - Field 'versions.endpoints' must not be empty",
          "Version 2.0 - Field 'versions.endpoints' must not be empty"
        )
      }
    }

    "validateKeysArePresent" should {
      "fail when the serviceName is empty" in {
        failsToValidate(ApiDefinitionValidator.validateKeysArePresent(calendarApi.copy(serviceName = ServiceName(""))))("Field 'serviceName' should not be empty")
      }
      "fail when the context is empty" in {
        failsToValidate(ApiDefinitionValidator.validateKeysArePresent(calendarApi.copy(context = ApiContext(""))))("Field 'context' should not be empty")
      }
      "fail when the serviceBaseUrl is empty" in {
        failsToValidate(ApiDefinitionValidator.validateKeysArePresent(calendarApi.copy(serviceBaseUrl = "")))("Field 'serviceBaseUrl' should not be empty")
      }
      "fail when the name is empty" in {
        failsToValidate(ApiDefinitionValidator.validateKeysArePresent(calendarApi.copy(name = "")))("Field 'name' should not be empty")
      }
    }

    "validate" when {
      "the API already exists" should {
        "collect multiple errors about changes" in {
          val previousContext        = ApiContext("individuals/almanac")
          val previousServiceBaseUrl = "http://almanac"
          val previousName           = "Almanac API"
          val deletedVersionNbr      = ApiVersionNbr("2.0")

          List(false, true).foreach { skipContextValidation =>
            failsToValidate(
              ApiDefinitionValidator.validate(
                requestedDefn = calendarApi,
                oExistingApiDefn = Some(calendarApi.copy(
                  context = previousContext,
                  serviceBaseUrl = previousServiceBaseUrl,
                  name = previousName,
                  versions = calendarApi.versions.appended(calendarApi.versions.head.copy(versionNbr = deletedVersionNbr))
                )),
                byContext = None,
                byServiceBaseUrl = None,
                byName = None,
                otherContextsWithSameTopLevel = List.empty,
                skipContextValidation
              ),
              numberOfErrors = 4
            )(
              s"Field 'context' cannot change from the previously published $previousContext",
              s"Field 'serviceBaseUrl' cannot change from the previously published $previousServiceBaseUrl",
              s"Field 'name' cannot change from the previously published $previousName",
              s"Versions may not be removed once published $deletedVersionNbr"
            )
          }
        }

        "fail when not skipping context validation if the context does not match the regular expression" in {
          val badContext = ApiContext("my_calendar")
          failsToValidate(ApiDefinitionValidator.validate(
            requestedDefn = calendarApi.copy(context = badContext),
            oExistingApiDefn = Some(calendarApi.copy(context = badContext)),
            byContext = None,
            byServiceBaseUrl = None,
            byName = None,
            otherContextsWithSameTopLevel = List.empty,
            skipContextValidation = false
          ))(
            s"$badContext - Field 'context' should match regular expression"
          )
        }

        "succeed when skipping context validation even if the context does not match the regular expression" in {
          val badContext = ApiContext("my_calendar")
          validates(ApiDefinitionValidator.validate(
            requestedDefn = calendarApi.copy(context = badContext),
            oExistingApiDefn = Some(calendarApi.copy(context = badContext)),
            byContext = None,
            byServiceBaseUrl = None,
            byName = None,
            otherContextsWithSameTopLevel = List.empty,
            skipContextValidation = true
          ))
        }
      }

      "the API is new" should {
        "collect multiple errors about uniqueness" in {
          List(false, true).foreach { skipContextValidation =>
            failsToValidate(
              ApiDefinitionValidator.validate(
                requestedDefn = calendarApi,
                oExistingApiDefn = None,
                byContext = Some(calendarApi),
                byServiceBaseUrl = Some(calendarApi),
                byName = Some(calendarApi),
                otherContextsWithSameTopLevel = List.empty,
                skipContextValidation
              ),
              numberOfErrors = 3
            )(
              s"Field 'context' must be unique but is used by ${calendarApi.serviceName}",
              s"Field 'serviceBaseUrl' must be unique but is used by ${calendarApi.serviceName}",
              s"Field 'name' must be unique but is used by ${calendarApi.serviceName}"
            )
          }
        }

        "fail when not skipping context validation if the context overlaps with another" in {
          val overlappingContext = ApiContext("individuals/calendar/events")
          List(false, true).foreach { skipContextValidation =>
            failsToValidate(
              ApiDefinitionValidator.validate(
                requestedDefn = calendarApi,
                oExistingApiDefn = None,
                byContext = None,
                byServiceBaseUrl = None,
                byName = None,
                otherContextsWithSameTopLevel = List(overlappingContext),
                skipContextValidation
              )
            )(
              s"${calendarApi.context} - Field 'context' overlaps with '$overlappingContext'"
            )
          }
        }

        "succeed when skipping context validation even if context does not have a valid top level" in {
          validates(
            ApiDefinitionValidator.validate(
              requestedDefn = calendarApi.copy(context = ApiContext("my/calendar")),
              oExistingApiDefn = None,
              byContext = None,
              byServiceBaseUrl = None,
              byName = None,
              otherContextsWithSameTopLevel = List.empty,
              skipContextValidation = true
            )
          )
        }
      }
    }
  }
}

//   implicit val mat: Materializer = app.materializer

//   trait Setup {
//     val mockAPIDefinitionService: APIDefinitionService       = mock[APIDefinitionService]
//     val mockApiDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]
//     val mockAppConfig: AppConfig                             = mock[AppConfig]
//     when(mockAppConfig.skipContextValidationAllowlist).thenReturn(List())
//     val apiContextValidator: ApiContextValidator             = new ApiContextValidator(mockAPIDefinitionService, mockApiDefinitionRepository, mockAppConfig)
//     val queryParameterValidator: QueryParameterValidator     = new QueryParameterValidator()
//     val apiEndpointValidator: ApiEndpointValidator           = new ApiEndpointValidator(queryParameterValidator)
//     val apiVersionValidator: ApiVersionValidator             = new ApiVersionValidator(apiEndpointValidator)
//     val apiDefinitionValidator: ApiDefinitionValidator       = new ApiDefinitionValidator(mockAPIDefinitionService, apiContextValidator, apiVersionValidator)

//     when(mockAPIDefinitionService.fetchByContext(*[ApiContext])).thenReturn(successful(None))
//     when(mockAPIDefinitionService.fetchByName(*[String])).thenReturn(successful(None))
//     when(mockAPIDefinitionService.fetchByServiceBaseUrl(*[String])).thenReturn(successful(None))
//     when(mockApiDefinitionRepository.fetchByServiceName(*[ServiceName])).thenReturn(successful(None))
//     when(mockApiDefinitionRepository.fetchAllByTopLevelContext(*[ApiContext])).thenReturn(successful(Seq.empty))

//     def assertValidationSuccess(apiDefinition: => StoredApiDefinition): Unit = {
//       val result = await(apiDefinitionValidator.validate(apiDefinition)(_ => successful(NoContent)))
//       result.header.status shouldBe NoContent.header.status
//       result.body.isKnownEmpty shouldBe true
//     }

//     def assertValidationFailure(apiDefinition: => StoredApiDefinition, failureMessages: Seq[String]): Unit = {

//       val result = apiDefinitionValidator.validate(apiDefinition)(_ => successful(NoContent))
//       status(result) shouldBe UnprocessableEntity.header.status

//       val validationErrors = contentAsJson(result).as[ValidationErrors]
//       validationErrors.code shouldBe INVALID_REQUEST_PAYLOAD

//       validationErrors.messages shouldBe failureMessages
//     }

//     val calendarApi = StoredApiDefinition(
//       ServiceName("calendar"),
//       "http://calendar",
//       "Calendar API",
//       "My Calendar API",
//       ApiContext("individuals/calendar"),
//       List(ApiVersion(
//         ApiVersionNbr("1.0"),
//         ApiStatus.BETA,
//         ApiAccess.PUBLIC,
//         List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//         endpointsEnabled = true
//       )),
//       false,
//       categories = List(ApiCategory.OTHER)
//     )
//   }

//   "ApiDefinitionValidator" should {

//     "fail validation if an empty serviceBaseUrl is provided" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(serviceBaseUrl = "")

//       assertValidationFailure(apiDefinition, List("Field 'serviceBaseUrl' should not be empty for API 'Calendar API'"))
//       verify(mockAPIDefinitionService, never).fetchByServiceBaseUrl(*[String])
//     }

//     "fail validation if an empty serviceName is provided" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(serviceName = ServiceName(""))

//       assertValidationFailure(apiDefinition, List("Field 'serviceName' should not be empty for API 'Calendar API'"))
//     }

//     "fail validation if a version number is referenced more than once" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(versions =
//         List(
//           ApiVersion(
//             ApiVersionNbr("1.0"),
//             ApiStatus.BETA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true
//           ),
//           ApiVersion(
//             ApiVersionNbr("1.1"),
//             ApiStatus.BETA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true
//           ),
//           ApiVersion(
//             ApiVersionNbr("1.1"),
//             ApiStatus.BETA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true
//           ),
//           ApiVersion(
//             ApiVersionNbr("1.2"),
//             ApiStatus.BETA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true
//           )
//         )
//       )

//       assertValidationFailure(apiDefinition, List("Field 'version' must be unique for API 'Calendar API'"))
//     }

//     "fail validation if an empty name is provided" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(name = "")

//       assertValidationFailure(apiDefinition, List("Field 'name' should not be empty for API with service name 'calendar'"))
//       verify(mockAPIDefinitionService, never).fetchByName(*[String])
//     }

//     "fail validation if an empty description is provided" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(description = "")

//       assertValidationFailure(apiDefinition, List("Field 'description' should not be empty for API 'Calendar API'"))
//     }

//     "fail validation if categories is empty" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(categories = List())

//       assertValidationFailure(apiDefinition, List("Field 'categories' should not be empty for API 'Calendar API'"))
//     }

//     "fail validation when no ApiVersion is provided" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(versions = Nil)
//       assertValidationFailure(apiDefinition, List("Field 'versions' must not be empty for API 'Calendar API'"))
//     }

//     "fail validation when if there is an API version without version number" in new Setup {
//       lazy val versions: List[ApiVersion]         =
//         List(
//           ApiVersion(
//             ApiVersionNbr("1.0"),
//             ApiStatus.BETA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true,
//             awsRequestId = None,
//             versionSource = ApiVersionSource.OAS
//           ),
//           ApiVersion(
//             ApiVersionNbr(""),
//             ApiStatus.BETA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true,
//             awsRequestId = None,
//             versionSource = ApiVersionSource.OAS
//           )
//         )
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(versions = versions)

//       assertValidationFailure(apiDefinition, List("Field 'versions.version' is required for API 'Calendar API'"))
//     }

//     "fail validation when if there is an ALPHA API version with endpoints enabled" in new Setup {
//       lazy val versions: List[ApiVersion]         =
//         List(
//           ApiVersion(
//             ApiVersionNbr("1.0"),
//             ApiStatus.ALPHA,
//             ApiAccess.PUBLIC,
//             List(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)),
//             endpointsEnabled = true,
//             awsRequestId = None,
//             versionSource = ApiVersionSource.OAS
//           )
//         )
//       lazy val apiDefinition: StoredApiDefinition = calendarApi.copy(versions = versions)

//       assertValidationFailure(apiDefinition, List("Field 'versions.endpointsEnabled' must be false for ALPHA status"))
//     }

//     "fail validation when no Endpoint is provided" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition =
//         calendarApi.copy(versions = List(ApiVersion(ApiVersionNbr("1.0"), ApiStatus.BETA, ApiAccess.PUBLIC, Nil, endpointsEnabled = true)))

//       assertValidationFailure(apiDefinition, List("Field 'versions.endpoints' must not be empty for API 'Calendar API' version '1.0'"))
//     }

//     val moneyEndpoint = Endpoint(
//       uriPattern = "/payments",
//       endpointName = "Check Payments",
//       method = HttpMethod.GET,
//       authType = AuthType.USER,
//       throttlingTier = ResourceThrottlingTier.UNLIMITED,
//       scope = Some("read:money")
//     )

//     val moneyApiVersion = ApiVersion(
//       versionNbr = ApiVersionNbr("1.0"),
//       status = ApiStatus.BETA,
//       endpoints = List(moneyEndpoint),
//       endpointsEnabled = true,
//       awsRequestId = None,
//       versionSource = ApiVersionSource.OAS
//     )

//     lazy val moneyApiDefinition = StoredApiDefinition(
//       serviceName = ServiceName("money"),
//       serviceBaseUrl = "http://www.money.com",
//       name = "Money API",
//       description = "API for checking payments",
//       context = ApiContext("individuals/money"),
//       versions = List(moneyApiVersion),
//       isTestSupport = false,
//       lastPublishedAt = None,
//       categories = List(ApiCategory.OTHER)
//     )

//     val specialChars = List(
//       ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
//       '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
//     )

//     "fail validation when name already exist for another API" in new Setup {
//       when(mockAPIDefinitionService.fetchByName("Money API"))
//         .thenReturn(successful(Some(ApiDefinition.fromStored(moneyApiDefinition.copy(serviceName = ServiceName("anotherService"))))))

//       assertValidationFailure(moneyApiDefinition, List("Field 'name' must be unique for API 'Money API'"))
//     }

//     "fail validation when serviceBaseUrl already exists for another API" in new Setup {
//       when(mockAPIDefinitionService.fetchByServiceBaseUrl("http://www.money.com"))
//         .thenReturn(successful(Some(ApiDefinition.fromStored(moneyApiDefinition.copy(serviceName = ServiceName("anotherService"))))))

//       assertValidationFailure(moneyApiDefinition, List("Field 'serviceBaseUrl' must be unique for API 'Money API'"))
//     }

//     "fail validation when the endpoint URI is empty" in new Setup {

//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = ""))))
//       )

//       assertValidationFailure(apiDefinition, List("Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
//     }

//     specialChars.foreach { char: Char =>
//       s"fail validation if the endpoint contains $char in the URI" in new Setup {
//         lazy val endpointUri                        = s"/payments$char"
//         lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//           versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpointUri))))
//         )

//         assertValidationFailure(
//           apiDefinition,
//           List(s"Field 'endpoints.uriPattern' with value '$endpointUri' should" +
//             " match regular expression '^/[.]?[a-zA-Z0-9_\\-\\/{}]*$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
//         )
//       }
//     }

//     "pass validation if the API definition contains the root endpoint" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = "/"))))
//       )
//       assertValidationSuccess(apiDefinition)
//     }

//     "fail validation if the endpoint has no name" in new Setup {
//       val endpoint                                = "/hello/friend"
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpoint, endpointName = ""))))
//       )

//       assertValidationFailure(apiDefinition, List(s"Field 'endpoints.endpointName' is required for API 'Money API' version '1.0'"))
//     }

//     "fail validation if the endpoint defines path parameters with ':'" in new Setup {
//       val endpoint                                = "/hello/:friend"
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpoint))))
//       )

//       assertValidationFailure(
//         apiDefinition,
//         List(s"Field 'endpoints.uriPattern' with value '$endpoint' should match regular expression '^/[.]?[a-zA-Z0-9_\\-\\/{}]*$$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
//       )
//     }

//     val pathParameterUris = Map(
//       "/{}"                -> "{}",
//       "/}{"                -> "}{",
//       "/hello{{friend}}"   -> "hello{{friend}}",
//       "/hello/my{brother}" -> "my{brother}",
//       "/hello/}friend{"    -> "}friend{",
//       "/hello/{0friend}"   -> "{0friend}"
//     )
//     pathParameterUris.foreach { case (endpointUri: String, segment: String) =>
//       s"fail validation if the endpoint ($endpointUri) defines path parameters incorrectly" in new Setup {
//         lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//           versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = endpointUri))))
//         )

//         assertValidationFailure(
//           apiDefinition,
//           List(s"Curly-bracketed segment '$segment' should match regular " +
//             "expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
//         )
//       }
//     }

//     s"fail validation if the endpoint defines multiple path parameters incorrectly" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = "/hello/{my/friend}"))))
//       )

//       assertValidationFailure(
//         apiDefinition,
//         List(
//           s"Curly-bracketed segment '{my' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'",
//           s"Curly-bracketed segment 'friend}' should match regular expression '^\\{[a-zA-Z]+[a-zA-Z0-9_\\-]*\\}$$' for API 'Money API' version '1.0' endpoint 'Check Payments'"
//         )
//       )
//     }

//     val moneyQueryParameter = QueryParameter("startDate")
//     "fail validation when a query parameter name is empty" in new Setup {

//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(queryParameters = List(moneyQueryParameter.copy(name = ""))))))
//       )

//       assertValidationFailure(apiDefinition, List("Field 'queryParameters.name' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
//     }

//     ('/' :: '{' :: '}' :: specialChars).foreach { char =>
//       s"fail validation when a query parameter name contains '$char' in the name" in new Setup {

//         val queryParamName                          = s"param$char"
//         lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//           versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(queryParameters = List(moneyQueryParameter.copy(name = queryParamName))))))
//         )

//         assertValidationFailure(
//           apiDefinition,
//           List(s"Field 'queryParameters.name' with value '$queryParamName' should" +
//             " match regular expression '^[a-zA-Z0-9_\\-]+$' for API 'Money API' version '1.0' endpoint 'Check Payments'")
//         )
//       }
//     }

//     "fail validation when no scopes are provided for a user restricted endpoint" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(scope = None))))
//       )
//       assertValidationFailure(apiDefinition, List("Field 'endpoints.scope' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"))
//     }

//     "pass validation when no scopes are provided for an application restricted endpoint" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(authType = AuthType.APPLICATION, scope = None))))
//       )
//       assertValidationSuccess(apiDefinition)
//     }

//     "pass validation when scopes are provided for an application restricted endpoint" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(authType = AuthType.APPLICATION, scope = Some("scope")))))
//       )
//       assertValidationSuccess(apiDefinition)
//     }

//     "accumulate multiple errors in the response" in new Setup {
//       lazy val apiDefinition: StoredApiDefinition = moneyApiDefinition.copy(
//         versions = List(moneyApiVersion.copy(endpoints = List(moneyEndpoint.copy(uriPattern = ""))))
//       )
//       when(mockAPIDefinitionService.fetchByContext(ApiContext("individuals/money")))
//         .thenReturn(successful(Some(ApiDefinition.fromStored(moneyApiDefinition.copy(serviceName = ServiceName("anotherService"))))))
//       when(mockAPIDefinitionService.fetchByName("Money API"))
//         .thenReturn(successful(Some(ApiDefinition.fromStored(moneyApiDefinition.copy(serviceName = ServiceName("anotherService"))))))
//       when(mockAPIDefinitionService.fetchByServiceBaseUrl("http://www.money.com"))
//         .thenReturn(successful(Some(ApiDefinition.fromStored(moneyApiDefinition.copy(serviceName = ServiceName("anotherService"))))))

//       assertValidationFailure(
//         apiDefinition,
//         List(
//           s"Field 'context' must be unique for API 'Money API'; Context: '${apiDefinition.context}'",
//           "Field 'name' must be unique for API 'Money API'",
//           "Field 'serviceBaseUrl' must be unique for API 'Money API'",
//           "Field 'endpoints.uriPattern' is required for API 'Money API' version '1.0' endpoint 'Check Payments'"
//         )
//       )
//     }

//     "detect ambiguity of different variables in the same path segment" in new Setup {
//       val values = Table(
//         ("UriPattern1", "UriPattern2", "Error message"),
//         (
//           "/{alpha}",
//           "/{beta}",
//           "Ambiguous path segment variables for API 'Calendar API' version '1.0': List(The variables {alpha} and {beta} cannot appear in the same segment in the endpoints /{alpha} and /{beta})"
//         ),
//         (
//           "/hello/{alpha}",
//           "/hello/{beta}",
//           "Ambiguous path segment variables for API 'Calendar API' version '1.0': List(The variables {alpha} and {beta} cannot appear in the same segment in the endpoints /hello/{alpha} and /hello/{beta})"
//         )
//       )

//       forAll(values) { case (uriPattern1, uriPattern2, errorMessage) =>
//         val apiDefinition: StoredApiDefinition = calendarApi.copy(versions =
//           List(calendarApi.versions.head.copy(
//             endpoints = List(
//               Endpoint(uriPattern1, "Endpoint1", HttpMethod.GET, AuthType.NONE),
//               Endpoint(uriPattern2, "Endpoint2", HttpMethod.GET, AuthType.NONE)
//             )
//           ))
//         )

//         assertValidationFailure(apiDefinition, List(errorMessage))
//       }
//     }

//     "not detect ambiguity of different variables in the same path segment" in new Setup {
//       val values = Table(
//         ("UriPattern1", "UriPattern2"),
//         ("/{alpha}/", "/world"),                           // Only one URI has a variable in root segment
//         ("/hello/{alpha}/", "/hello/world"),               // Only one URI has a variable in segment 2
//         ("/hello/{alpha}/{beta}", "/hello/world/{gamma}"), // OK up to segment 2, so variables in segment 3 don't matter
//         ("/hello/world/{alpha}/", "/hello/World/{beta}")   // Different cases for 'world', so variables in segment 3 don't matter
//       )

//       forAll(values) { case (uriPattern1, uriPattern2) =>
//         val apiDefinition: StoredApiDefinition = calendarApi.copy(versions =
//           List(calendarApi.versions.head.copy(
//             endpoints = List(
//               Endpoint(uriPattern1, "Endpoint1", HttpMethod.GET, AuthType.NONE),
//               Endpoint(uriPattern2, "Endpoint2", HttpMethod.GET, AuthType.NONE)
//             )
//           ))
//         )

//         assertValidationSuccess(apiDefinition)
//       }
//     }
//   }
