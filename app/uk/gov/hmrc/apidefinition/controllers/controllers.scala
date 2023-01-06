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

package uk.gov.hmrc.apidefinition

import scala.concurrent.Future
import scala.concurrent.Future.successful

import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsPath, JsResult, JsValue, JsonValidationError, Reads}
import play.api.mvc.Results.UnprocessableEntity
import play.api.mvc.{Request, Result}

import uk.gov.hmrc.apidefinition.models.{ErrorCode, ErrorResponse, FieldErrorDescription}

package object controllers {

  def validate[T](request: Request[JsValue])(implicit tjs: Reads[T]): Either[Result, JsResult[T]] = {
    try {
      Right(request.body.validate[T])
    } catch {
      case e: Throwable => Left(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage)))
    }
  }

  /** If the request body is valid then execute the provided function to return a result. If the request body is not valid then return the error result as specified below.
    */
  def handleRequest[T](request: Request[JsValue])(f: T => Future[Result])(implicit tjs: Reads[T]): Future[Result] = {
    val either: Either[Result, JsResult[T]] = validate(request)
    either.fold(
      successful,
      {
        result =>
          result.fold(
            errors => successful(UnprocessableEntity(validationResult(errors))),
            entity => f(entity)
          )
      }
    )
  }

  /** Used to improve the error messages that request.body.validate might return.
    */
  def validationResult(errors: Seq[(JsPath, Seq[JsonValidationError])]): JsValue = {
    val errs: Seq[FieldErrorDescription] = errors flatMap { case (jsPath, seqValidationError) =>
      seqValidationError map {
        validationError =>
          val isMissingPath = validationError.message == "error.path.missing"
          val message       = if (isMissingPath) "element is missing" else validationError.message
          FieldErrorDescription(jsPath.toString, message)
      }
    }
    toJson(ErrorResponse(ErrorCode.API_INVALID_JSON, "Json cannot be converted to API Definition", Some(errs)))
  }

  def error(code: ErrorCode.Value, message: String): JsValue = {
    toJson(ErrorResponse(code, message))
  }

}
