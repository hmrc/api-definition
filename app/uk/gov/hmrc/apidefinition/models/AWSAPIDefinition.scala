/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.models

import uk.gov.hmrc.apidefinition.models.AWSParameterType.AWSParameterType

case class AWSAPIDefinition()

case class AWSSwaggerDetails(paths: Map[String, Map[String, AWSHttpVerbDetails]],
                              info: AWSAPIInfo,
                              swagger: String = "2.0",
                              basePath: Option[String] = None,
                              host: Option[String] = None)

case class AWSAPIInfo(title: String, version: String)

case class AWSHttpVerbDetails(parameters: Option[Seq[AWSParameter]], responses: Map[String, AWSResponse])

case class AWSResponse(description: String)

abstract class AWSParameter(val name: String,
                             val required: Boolean,
                             val in: AWSParameterType,
                             val `type`: String = AWSParameter.defaultParameterType,
                             val description: String = AWSParameter.defaultParameterDescription) {
}

object AWSParameter {
  val defaultParameterType = "string"
  val defaultParameterDescription = ""
}

case class AWSQueryParameter(override val name: String,
                              override val required: Boolean,
                              override val in: AWSParameterType = AWSParameterType.QUERY,
                              override val `type`: String = AWSParameter.defaultParameterType,
                              override val description: String = AWSParameter.defaultParameterDescription)
  extends AWSParameter(name, required, in, `type`, description) {
}

case class AWSPathParameter(override val name: String,
                             override val required: Boolean = true,
                             override val in: AWSParameterType = AWSParameterType.PATH,
                             override val `type`: String = AWSParameter.defaultParameterType,
                             override val description: String = AWSParameter.defaultParameterDescription)
  extends AWSParameter(name, required, in, `type`, description) {
}

object AWSParameterType extends Enumeration {
  type AWSParameterType = Value

  val QUERY = Value("query")
  val PATH = Value("path")
}

object AWSAPIDefinition {
  def awsApiGatewayName(version: String, apiDefinition: APIDefinition): String =
    s"${apiDefinition.context.replaceAll("/", "--")}--$version"
}