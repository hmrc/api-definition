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

import scala.collection.immutable.TreeMap
import uk.gov.hmrc.apidefinition.models.AWSAPIDefinition._
import uk.gov.hmrc.apidefinition.models._
import scala.language.postfixOps
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

object AWSPayloadHelper {

  def buildAWSSwaggerDetails(apiName: String, apiVersion: ApiVersion, basePath: ApiContext, host: String): AWSSwaggerDetails = {
    AWSSwaggerDetails(
      paths = buildAWSPaths(apiVersion),
      info = AWSAPIInfo(apiName, apiVersion.version),
      basePath = Some(s"/${basePath.value}"),
      host = Some(host)
    )
  }

  private def buildAWSPaths(apiVersion: ApiVersion): Map[String, Map[String, AWSHttpVerbDetails]] = {

    def buildAWSHttpVerbDetails(e: Endpoint): AWSHttpVerbDetails = {
      AWSHttpVerbDetails(
        parameters = buildAWSParameters(e),
        responses = Map("200" -> AWSResponse(description = "OK")),
        `x-auth-type` = awsAuthType(e.authType),
        `x-throttling-tier` = awsThrottlingTier(e.throttlingTier),
        `x-scope` = e.scope
      )
    }

    def buildHttpVerbsDetails(resourceToEndpoints: Map[String, Seq[Endpoint]]): Map[String, Map[String, AWSHttpVerbDetails]] = {
      resourceToEndpoints.view.mapValues { endpoints: Seq[Endpoint] =>
        endpoints.map { e: Endpoint =>
          (e.method.toString.toLowerCase, buildAWSHttpVerbDetails(e))
        }.groupBy(_._1).view.mapValues(_.head._2).toMap
      } toMap
    }

    def groupEndpointsByResource(endpoints: Seq[Endpoint]): Map[String, Seq[Endpoint]] = {
      endpoints.groupBy(_.uriPattern)
    }

    // sorting alphabetically by resource
    import scala.math.Ordering.String
    TreeMap() ++ buildHttpVerbsDetails(groupEndpointsByResource(apiVersion.endpoints))
  }

  def buildAWSParameters(endpoint: Endpoint): Option[Seq[AWSParameter]] = {
    Option(buildAWSPathParameters(endpoint) ++ buildAWSQueryParameters(endpoint)).filter(_.nonEmpty)
  }

  def buildAWSPathParameters(endpoint: Endpoint): Seq[AWSPathParameter] = {
    RegexHelper.extractPathParameters(endpoint.uriPattern).map {
      param: String => AWSPathParameter(name = param)
    }
  }

  def buildAWSQueryParameters(endpoint: Endpoint): Seq[AWSQueryParameter] = {
    endpoint.queryParameters.getOrElse(Seq()).map {
      p: QueryParameter => AWSQueryParameter(name = p.name, required = p.required)
    }.sortBy(_.name)
  }
}
