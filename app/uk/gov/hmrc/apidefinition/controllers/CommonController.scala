/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.controllers

import play.api.Logger
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.apidefinition.models.ErrorCode
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait CommonController extends BaseController {

  implicit val ec: ExecutionContext

  override protected def withJsonBody[T](f: T => Future[Result])
                                        (implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {

    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => Future(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, JsError.toJson(errs))))
      case Failure(e) => Future(UnprocessableEntity(error(ErrorCode.INVALID_REQUEST_PAYLOAD, e.getMessage)))
    }
  }

  def recovery: PartialFunction[Throwable, Result] = {
    case e =>
      val message = s"An unexpected error occurred: ${e.getMessage}"
      Logger.error(message, e)
      InternalServerError(error(ErrorCode.INTERNAL_SERVER_ERROR, message))
  }

  private def error(errorCode: ErrorCode.Value, message: JsValueWrapper): JsObject = {
    Json.obj(
      "code" -> errorCode.toString,
      "message" -> message
    )
  }
}
