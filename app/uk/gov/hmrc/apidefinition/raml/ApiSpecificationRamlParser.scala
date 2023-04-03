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

package uk.gov.hmrc.apidefinition.raml

import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters._

import org.raml.v2.api.model.v10.datamodel.{ExampleSpec => RamlExampleSpec, StringTypeDeclaration => RamlStringTypeDeclaration, TypeDeclaration => RamlTypeDeclaration}
import org.raml.v2.api.model.v10.methods.{Method => RamlMethod}
import org.raml.v2.api.model.v10.resources.{Resource => RamlResource}

import uk.gov.hmrc.apidefinition.models.apispecification._
import uk.gov.hmrc.apidefinition.raml.RamlSyntax._
import uk.gov.hmrc.apidefinition.services.SchemaService

@Singleton
class ApiSpecificationRamlParser @Inject() (schemaService: SchemaService) {

  def toApiSpecification(basePath: String, raml: RAML.RAML): ApiSpecification = {

    def title: String = SafeValueAsString(raml.title)

    def version: String = raml.version.value

    def deprecationMessage: Option[String] = raml.annotation("(deprecationMessage)")

    def documentationItems: List[DocumentationItem] =
      raml.documentation.asScala.toList.map(item =>
        DocumentationItem(
          SafeValueAsString(item.title),
          SafeValueAsString(item.content)
        )
      )

    lazy val resources = output(globalTypes).map(_.resource)

    lazy val groupMap = ResourcesAndGroups.flatten(output(globalTypes).map(_.groupMap))

    def resourceGroups: List[ResourceGroup] = ResourceGroup.generateFrom(resources, groupMap)

    def types: List[TypeDeclaration] = (raml.types.asScala.toList ++ raml.uses.asScala.flatMap(_.types.asScala))
      .map(toTypeDeclaration(basePath))

    def isFieldOptionalityKnown: Boolean = !raml.hasAnnotation("(fieldOptionalityUnknown)")

    lazy val globalTypes = types

    def output(typesToSearch: List[TypeDeclaration]): List[ResourcesAndGroups] = raml.resources.asScala.toList.map(toResourcesAndGroups(basePath, typesToSearch))

    ApiSpecification(
      title,
      version,
      deprecationMessage,
      documentationItems,
      resourceGroups,
      globalTypes,
      isFieldOptionalityKnown
    )

  }

  private def toExampleSpec(example: RamlExampleSpec): ExampleSpec = {

    val description: Option[String] = {
      example.structuredValue.property("description", "value")
    }

    val documentation: Option[String] = {
      example.annotation("(documentation)")
    }

    val code: Option[String] = {
      example.structuredValue.property("value", "code")
        .orElse(example.structuredValue.property("code"))
    }

    val value: Option[String] = {
      example.structuredValue.property("value")
        .orElse(SafeValue(example))
    }
    ExampleSpec(description, documentation, code, value)
  }

  private def fromTypeDeclaration(basePath: String)(td: RamlTypeDeclaration): TypeDeclaration = td match {
    case t: RamlStringTypeDeclaration =>
      TypeDeclaration(
        name = td.name(),
        displayName = SafeValueAsString(t.displayName),
        `type` = toType(t.`type`, basePath),
        required = t.required(),
        description = SafeValue(t.description()).map(_.toString()),
        examples = fromExamples(t),
        enumValues = t.enumValues().asScala.toList,
        pattern = SafeValue(t.pattern)
      )

    case t: RamlTypeDeclaration =>
      TypeDeclaration(
        name = td.name(),
        displayName = SafeValueAsString(t.displayName),
        `type` = toType(t.`type`, basePath),
        required = t.required(),
        description = SafeValue(t.description()).map(_.toString()),
        examples = fromExamples(t),
        enumValues = List(),
        pattern = None
      )
  }

  private def fromExamples(r: RamlTypeDeclaration): List[ExampleSpec] = {
    if (r.example != null) {
      List(toExampleSpec(r.example))
    } else {
      r.examples.asScala.toList.map(toExampleSpec)
    }
  }

  private def toTypeDeclaration(basePath: String, globalTypes: List[TypeDeclaration] = List())(td: RamlTypeDeclaration): TypeDeclaration = {
    def findType(typeName: String) =
      globalTypes.find(_.name == typeName)

    val thisType = fromTypeDeclaration(basePath)(td)

    val finalisedTypeDeclaration =
      findType(td.`type`).fold(thisType)(parent =>
        thisType.copy(
          pattern = thisType.pattern.orElse(parent.pattern),
          examples = if (thisType.examples.isEmpty) parent.examples else thisType.examples
        )
      )

    finalisedTypeDeclaration
  }

  private def toType(`type`: String, basePath: String): String = {

    def isSchema(text: String): Boolean = text.trim.startsWith("{")

    if (isSchema(`type`)) {
      val inlinedJsonSchema: JsonSchema = schemaService.parseSchema(`type`, basePath)
      schemaService.toJsonString(inlinedJsonSchema)
    } else {
      `type`
    }
  }

  private def toResourcesAndGroups(basePath: String, globalTypes: List[TypeDeclaration])(ramlResource: RamlResource): ResourcesAndGroups = {
    val childNodes: List[ResourcesAndGroups] = ramlResource.resources().asScala.toList.map(toResourcesAndGroups(basePath, globalTypes))

    val children = childNodes.map(_.resource)

    val group = if (ramlResource.hasAnnotation("(group)")) {
      val groupName = ramlResource.annotation("(group)", "name").getOrElse("")
      val groupDesc = ramlResource.annotation("(group)", "description").getOrElse("")
      Some(Group(groupName, groupDesc))
    } else {
      None
    }

    val resource = Resource(
      resourcePath = ramlResource.resourcePath,
      methods = methodsForResource(basePath)(ramlResource),
      relativeUri = ramlResource.relativeUri.value,
      uriParameters = ramlResource.uriParameters.asScala.toList.map(toTypeDeclaration(basePath, globalTypes)),
      displayName = ramlResource.displayName.value,
      children = children
    )

    val thisMap: ResourcesAndGroups.GroupMap         = group.fold(ResourcesAndGroups.emptyGroupMap)(g => Map(resource -> g))
    val childMaps: List[ResourcesAndGroups.GroupMap] = childNodes.map(_.groupMap)

    ResourcesAndGroups(resource, ResourcesAndGroups.flatten(thisMap, childMaps))
  }

  private def methodsForResource(basePath: String)(resource: RamlResource): List[Method] = {
    val correctOrder = Map("get" -> 0, "post" -> 1, "put" -> 2, "delete" -> 3, "head" -> 4, "patch" -> 5, "options" -> 6)

    resource.methods.asScala.toList.sortWith { (left, right) =>
      (for {
        l <- correctOrder.get(left.method)
        r <- correctOrder.get(right.method)
      } yield l < r).getOrElse(false)
    }
      .map(toMethod(basePath))
  }

  private def toMethod(basePath: String)(ramlMethod: RamlMethod): Method = {
    val queryParameters = ramlMethod.queryParameters.asScala.toList.map(toTypeDeclaration(basePath))
    val headers         = ramlMethod.headers.asScala.toList.map(toTypeDeclaration(basePath))
    val body            = ramlMethod.body.asScala.toList.map(toTypeDeclaration(basePath))

    def fetchAuthorisation: Option[SecurityScheme] = {
      if (ramlMethod.securedBy().asScala.nonEmpty) {
        ramlMethod.securedBy.get(0).securityScheme.`type` match {
          case "OAuth 2.0" => Some(SecurityScheme("user", ramlMethod.annotation("(scope)")))
          case _           => Some(SecurityScheme("application", ramlMethod.annotation("(scope)")))
        }
      } else {
        None
      }
    }

    def responses: List[Response] = {
      ramlMethod.responses().asScala.toList.map(r => {
        Response(
          code = SafeValueAsString(r.code()),
          body = r.body.asScala.toList.map(toTypeDeclaration(basePath)),
          headers = r.headers().asScala.toList.map(toTypeDeclaration(basePath)),
          description = SafeValue(r.description())
        )
      })
    }

    def sandboxData = ramlMethod.annotation("(sandboxData)")

    Method(
      ramlMethod.method,
      SafeValueAsString(ramlMethod.displayName),
      body,
      headers,
      queryParameters,
      SafeValue(ramlMethod.description),
      fetchAuthorisation,
      responses,
      sandboxData
    )
  }
}
