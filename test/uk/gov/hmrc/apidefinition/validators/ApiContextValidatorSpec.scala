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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApiContext

class ApiContextValidatorSpec extends AbstractValidatorSpec {

  "ApiContextValidator" when {
    "validateTopLevelContext" when {
      "skipping validation" should {
        "succeed if context has a valid top-level context" in {
          validates(ApiContextValidator.validateTopLevelContext(true)(ApiContext("test/my-context")))
        }

        "succeed if context does not have a valid top-level context" in {
          validates(ApiContextValidator.validateTopLevelContext(true)(ApiContext("hello")))
        }
      }

      "not skipping validation" should {
        "succeed if context has a valid top-level context" in {
          validates(ApiContextValidator.validateTopLevelContext(false)(ApiContext("test/my-context")))
        }

        "fail if context does not have a valid top-level context" in {
          failsToValidate(ApiContextValidator.validateTopLevelContext(false)(ApiContext("hello")))("Field 'context' must start with one of")
        }
      }
    }

    "validateContext" when {
      "skipping validation" should {
        "succeed when the context has no empty path segments and matches the regular expression" in {
          validates(ApiContextValidator.validateContext(true)(ApiContext("test/my-context")))
        }

        "not have empty path segments" in {
          failsToValidate(ApiContextValidator.validateContext(true)(ApiContext("test//my-context")))("Field 'context' should not have empty path segments")
        }

        "succeed even though it does not match the regular expression" in {
          validates(ApiContextValidator.validateContext(true)(ApiContext("test/my_context")))
        }
      }

      "not skipping validation" should {
        "succeed when the context has no empty path segments and matches the regular expression" in {
          validates(ApiContextValidator.validateContext(false)(ApiContext("test/my-context")))
        }

        "not have empty path segments" in {
          failsToValidate(ApiContextValidator.validateContext(false)(ApiContext("test//my-context")))("Field 'context' should not have empty path segments")
        }

        "match the regular expression" in {
          failsToValidate(ApiContextValidator.validateContext(false)(ApiContext("test/my_context")))("Field 'context' should match regular expression")
        }
      }
    }

    "validateContextHasAtLeastTwoSegments" when {
      "skipping validation" should {
        "succeed when the context has two segments" in {
          validates(ApiContextValidator.validateContextHasAtLeastTwoSegments(true)(ApiContext("test/my-context")))
        }

        "succeed even though the context has one segment" in {
          validates(ApiContextValidator.validateContextHasAtLeastTwoSegments(true)(ApiContext("my-context")))
        }
      }

      "not skipping validation" should {
        "succeed when the context has two segments" in {
          validates(ApiContextValidator.validateContextHasAtLeastTwoSegments(false)(ApiContext("test/my-context")))
        }

        "fail when the context has one segment" in {
          failsToValidate(ApiContextValidator.validateContextHasAtLeastTwoSegments(false)(ApiContext("my-context")))("Field 'context' must have at least two segments")
        }
      }
    }

    "validateContextDoesNotOverlapExistingAPI" should {
      val twoPartContext   = ApiContext("individuals/foo")
      val threePartContext = ApiContext("individuals/foo/bar")

      "fail when new API context is subset of existing" in {
        failsToValidate(ApiContextValidator.validateContextDoesNotOverlapExistingAPI(twoPartContext, List(threePartContext)))(s"Field 'context' overlaps with '$threePartContext'")
      }

      "fail when new API context is superset of existing" in {
        failsToValidate(ApiContextValidator.validateContextDoesNotOverlapExistingAPI(threePartContext, List(twoPartContext)))(s"Field 'context' overlaps with '$twoPartContext'")
      }

      "succeed when new API context does not overlap multiple other APIs in same top level context" in {
        val nonOverlappingContexts = List(ApiContext("individuals/bar"), ApiContext("individuals/baz"))
        validates(ApiContextValidator.validateContextDoesNotOverlapExistingAPI(twoPartContext, nonOverlappingContexts))
      }

      "succeed when new API context string overlaps but does not path overlap" in {
        val nonOverlappingContexts = List(ApiContext("individuals/fo"), ApiContext("individuals/fooo"))
        validates(ApiContextValidator.validateContextDoesNotOverlapExistingAPI(twoPartContext, nonOverlappingContexts))
      }
    }

    "validateForExistingAPI" when {
      "skipping validation" should {
        "succeed even with a wrong top level and not matching the regular expression" in {
          validates(ApiContextValidator.validateForExistingAPI(true)(ApiContext("hello/my_world")))
        }

        "succeed with a good context, disregarding top level segment check" in {
          validates(ApiContextValidator.validateForExistingAPI(true)(ApiContext("hello/my-world")))
        }
      }

      "not skipping validation" should {
        "collect all errors when failing" in {
          val badContext = ApiContext("hello/my_world")
          failsToValidate(ApiContextValidator.validateForExistingAPI(false)(badContext))(
            s"$badContext - Field 'context' should match regular expression"
          )
        }

        "succeed with a good context, disregarding top level segment check" in {
          validates(ApiContextValidator.validateForExistingAPI(false)(ApiContext("hello/my-world")))
        }
      }
    }

    "validateForNewAPI" when {
      "skipping validation" should {
        "succeed even with a wrong top level and only one segment" in {
          validates(ApiContextValidator.validateForNewAPI(true)(ApiContext("hello"), List.empty))
        }

        "succeed with a good context" in {
          validates(ApiContextValidator.validateForNewAPI(true)(ApiContext("individuals/foo"), List.empty))
        }
      }

      "not skipping validation" should {
        "collect all errors when failing" in {
          val badContext         = ApiContext("hello")
          val overlappingContext = ApiContext("hello/world")
          failsToValidate(ApiContextValidator.validateForNewAPI(false)(badContext, List(overlappingContext)), numberOfErrors = 3)(
            s"$badContext - Field 'context' must start with one of",
            s"$badContext - Field 'context' must have at least two segments",
            s"$badContext - Field 'context' overlaps with '$overlappingContext'"
          )
        }

        "succeed with a good context" in {
          validates(ApiContextValidator.validateForNewAPI(false)(ApiContext("individuals/foo"), List.empty))
        }
      }
    }
  }
}
