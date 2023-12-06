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

import uk.gov.hmrc.apidefinition.validators.ApiEndpointValidator
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{AuthType, Endpoint, HttpMethod, ResourceThrottlingTier}
import cats.data.Validated

class ApiEndpointValidatorSpec extends AsyncHmrcSpec {

  trait Setup {
    val specialChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(', ')'
    )
    val queryParameterValidator: QueryParameterValidator     = new QueryParameterValidator()
    val validator = new ApiEndpointValidator(queryParameterValidator)

    val endpointWithDot: Endpoint  = Endpoint("/.well-known/openid-configuration", "Test Endpoint", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)
    val endpointWithoutDot: Endpoint  = Endpoint("/well-known/openid-configuration", "Test Endpoint", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)
    val existingValidEndpoint: Endpoint  = Endpoint("/paye/{nino}/eligibility-check-digitally-excluded", "Test Endpoint", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)
  }



  "ApiEndpointValidator" should {
    "allow dots in endpoints" in new Setup {

      val x = validator.validate("Error Message")(endpointWithDot)

      x match {
        case Validated.Valid(_) => succeed
        case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
      }
    }
    
    "allow endpoints without dots" in new Setup {

      val x = validator.validate("Error Message")(endpointWithoutDot)

      x match {
        case Validated.Valid(_) => succeed
        case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
      }
    }

   "stop invalid endpoints" ignore new Setup {

      val x = validator.validate("Error Message")(endpointWithDot.copy(uriPattern = "/IAMINVALID£££"))

      x match {
        case Validated.Valid(_) => fail()
        case Validated.Invalid(errors) => errors.toList.mkString shouldBe "hello"
      }
    }

    "allow valid endpoints" in new Setup {

      val x = validator.validate("Error Message")(existingValidEndpoint)

      x match {
        case Validated.Valid(_) => succeed
        case Validated.Invalid(errors) => fail(s"endpoint validation failed ${errors.toList.mkString}")
      }
    }

 
    "fail validation if the endpoint contains in the URI" in new Setup {
       specialChars.foreach { char: Char =>
        val endpointUri                        = s"/payments$char"
         val x  =  validator.validate("Error Message")(existingValidEndpoint.copy(uriPattern = endpointUri))
        
        x match {
          case Validated.Valid(_) => fail(s"$char should fail validation")
          case Validated.Invalid(errors) => {
            println(errors.toList.mkString)
            succeed
          }
        }
      }
    }
  }
}
