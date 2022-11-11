/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.models.raml

import uk.gov.hmrc.apidefinition.services.SchemaService
import scala.io.Source
import uk.gov.hmrc.apidefinition.raml.ApiSpecificationRamlParser
import uk.gov.hmrc.apidefinition.models.apispecification.JsonSchema

object SchemaTestHelper {

  object TestSchemaService extends SchemaService {

    override def fetchPlainTextSchema(uri: String): String = {
      val source = Source.fromFile(uri)
      val text   = source.mkString
      source.close()
      text
    }

    def parseSchema(uri: String): JsonSchema = {
      parseSchema(fetchPlainTextSchema(s"${SchemaTestHelper.basePath}/${uri}"), basePath)
    }

    def parseSchemaInFolder(uri: String, basePathExtension: String): JsonSchema = {
      val altBasePath = if (basePathExtension.isEmpty) SchemaTestHelper.basePath else s"${SchemaTestHelper.basePath}/${basePathExtension}"

      parseSchema(fetchPlainTextSchema(s"${altBasePath}/${uri}"), altBasePath)
    }
  }

  val basePath = "test/resources"

  val apiSpecificationRamlParser = new ApiSpecificationRamlParser(TestSchemaService)
}
