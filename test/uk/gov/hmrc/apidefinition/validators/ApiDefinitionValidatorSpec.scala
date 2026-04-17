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
    ApiAccessType.PUBLIC,
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
              s"Versions ($deletedVersionNbr) may not be removed once published"
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
