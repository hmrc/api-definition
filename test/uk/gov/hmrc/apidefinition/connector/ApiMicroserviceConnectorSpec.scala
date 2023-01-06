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

package uk.gov.hmrc.apidefinition.connector

import akka.stream.scaladsl.Source
import akka.util.ByteString
import mockws.{MockWS, MockWSHelpers}
import org.scalatest.BeforeAndAfterAll
import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.mvc.{ResponseHeader, Result, Results}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apidefinition.utils.Utils
import uk.gov.hmrc.apidefinition.utils.AsyncHmrcSpec

class ApiMicroserviceConnectorSpec extends AsyncHmrcSpec with Utils with MockWSHelpers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    shutdownHelpers()
  }

  val serviceUrl = "http://localhost"

  val serviceName         = "hello-world"
  val version             = "1.0"
  val resourceFoundUrl    = s"$serviceUrl/api/conf/$version/resource"
  val streamedResourceUrl = s"$serviceUrl/api/conf/$version/streamedResource"
  val resourceNotFoundUrl = s"$serviceUrl/api/conf/$version/resourceNotThere"

  val testFileName                  = "hello"
  val source: Source[ByteString, _] = createSourceFrom(testFileName)
  val sourceContents                = contentsFrom(testFileName)

  val mockWS = MockWS {
    case ("GET", `resourceFoundUrl`)    => Action(Results.Ok("hello world"))
    case ("GET", `streamedResourceUrl`) => Action(Result(
        header = ResponseHeader(OK, Map("Content-length" -> s"${sourceContents.length()}")),
        body = HttpEntity.Streamed(source, Some(sourceContents.length()), Some("application/pdf"))
      ))
    case ("GET", `resourceNotFoundUrl`) => Action(Results.NotFound)
  }

  trait Setup {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc = HeaderCarrier()
    val underTest   = new ApiMicroserviceConnector(mockWS)
  }

  "fetchApiDocumentationResourceByUrl" should {

    "return the resource" in new Setup {

      val result = await(underTest.fetchApiDocumentationResourceByUrl(serviceUrl, version, "resource"))

      result.status should be(OK)
      contentsFrom(result) should be("hello world")
    }

    "return streamed resource" in new Setup {

      val result = await(underTest.fetchApiDocumentationResourceByUrl(serviceUrl, version, "streamedResource"))

      result.status should be(OK)
      contentsFrom(result) should be("HELLO\n")
    }

  }
}
