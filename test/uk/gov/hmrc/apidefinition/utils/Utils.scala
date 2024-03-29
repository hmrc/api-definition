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

package uk.gov.hmrc.apidefinition.utils

import java.nio.file.Paths
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source}
import org.apache.pekko.stream.{IOResult, Materializer}
import org.apache.pekko.util.ByteString

import play.api.libs.ws.WSResponse

trait Utils {

  implicit val materializer: Materializer

  private val defaultTimeout: FiniteDuration = 5 seconds

  def createSourceFrom(fileName: String): Source[ByteString, Future[IOResult]] = {
    val path = Paths.get(getClass.getResource("/" + fileName).toURI)
    FileIO.fromPath(path)
  }

  def contentsFrom(fileName: String): String = {
    val stream = getClass.getResourceAsStream("/" + fileName)
    if (stream != null) scala.io.Source.fromInputStream(stream).mkString else fileName

  }

  def contentsFrom(response: WSResponse): String = {
    contentsFrom(response.body)
  }

  def contentsFrom(source: Source[ByteString, _]): String = {
    val sink = Sink.fold[String, ByteString]("") { (content, bytes) =>
      content + bytes.utf8String
    }
    Await.result(source.runWith(sink), defaultTimeout)
  }

}
