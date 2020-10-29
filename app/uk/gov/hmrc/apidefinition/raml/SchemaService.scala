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

package uk.gov.hmrc.apidefinition.services

import java.net.URI

import play.api.libs.json.Json

import scala.collection.immutable.ListMap
import scala.io.Source
import uk.gov.hmrc.apidefinition.models.apispecification.JsonSchema
import uk.gov.hmrc.apidefinition.models.apispecification.JsonSchema.JsonSchemaWithReference
import javax.inject.Singleton
import play.api.Logger

@Singleton
class SchemaService {

  protected def fetchPlainTextSchema(uri: String): String = {
    var source: Source = null
    try {
      source = Source.fromURL(uri)
      source.mkString
    }
    finally {
      if(source != null) {
        source.close()
        source = null
      }
    }
  }

  private def fetchSchema(basePath: String, schemaPath: String): JsonSchema = {

    val schemaLocation = if (URI.create(schemaPath).isAbsolute) {
      schemaPath
    } else {
      URI.create(s"$basePath/$schemaPath").normalize.toString
    }

    val newBasePath = schemaLocation.substring(0, schemaLocation.lastIndexOf('/'))

    parseSchema(fetchPlainTextSchema(schemaLocation), newBasePath)
  }

  def parseSchema(schema: String, basePath: String): JsonSchema = {
    
    Logger.info(s"SCHEMA :[$schema]")
    val jsonSchema = Json.parse(schema).as[JsonSchema]
    jsonSchema match {
      case JsonSchemaWithReference() => {
        resolveRefs(jsonSchema, basePath, jsonSchema)
      }

      case s => s
    }
  }

  def toJsonString(jsonSchema: JsonSchema): String = {
    Json.stringify(Json.toJson(jsonSchema))
  }

  private def resolveRefs(schema: JsonSchema, basePath: String, enclosingSchema: JsonSchema): JsonSchema = {
    def resolve(schema: JsonSchema, basePath: String, enclosingSchema: JsonSchema)(ref: String) = {
      val (referredSchemaPath, jsonPointerPathParts) = getPath(ref)

      val referredSchema = referredSchemaPath match {
        case "" => enclosingSchema
        case _  => fetchSchema(basePath, referredSchemaPath)
      }

      val referredSubSchema = findSubschema(jsonPointerPathParts, referredSchema)
      schema.description.fold(referredSubSchema)(description => referredSubSchema.copy(description = Some(description)))
    }

    @scala.annotation.tailrec
    def findSubschema(pathParts: Seq[String], schema: JsonSchema): JsonSchema = {
      pathParts match {
        case "definitions" +: pathPart +: Nil => {
          val resolved = schema.definitions(pathPart)
          resolved match {
            case JsonSchemaWithReference()  =>
              resolveRefs(resolved, basePath, enclosingSchema)
            case _       =>
              resolved
          }
        }
        case "definitions" +: pathPart +: remainingPathParts =>
          findSubschema(remainingPathParts, schema.definitions(pathPart))

        case pathPart +: Nil =>
          schema.properties(pathPart)

        case pathPart +: remainingPath =>
          findSubschema(remainingPath, schema.properties(pathPart))

        case _ => schema
      }
    }

    def resolveRefsInSubschemas(subschemas: ListMap[String, JsonSchema], basePath: String, enclosingSchema: JsonSchema): ListMap[String, JsonSchema] = {
      subschemas.map { case (name, subSchema) =>
        name -> resolveRefs(subSchema, basePath, enclosingSchema)
      }
    }

    def resolveRefsInOneOfs(oneOfs: Seq[JsonSchema], basePath: String, enclosingSchema: JsonSchema): Seq[JsonSchema] = {
      oneOfs.map(resolveRefs(_, basePath, enclosingSchema))
    }

    schema.ref match {
      case Some(ref) =>
        val x = resolve(schema, basePath, enclosingSchema)(ref)
        x
      case _ =>
        val properties = resolveRefsInSubschemas(schema.properties, basePath, enclosingSchema)
        val patternProperties = resolveRefsInSubschemas(schema.patternProperties, basePath, enclosingSchema)
        val definitions = resolveRefsInSubschemas(schema.definitions, basePath, enclosingSchema)
        val items = schema.items.map(resolveRefs(_, basePath, enclosingSchema))
        val oneOfs = resolveRefsInOneOfs(schema.oneOf, basePath, enclosingSchema)

        schema.copy(
          properties = properties,
          patternProperties = patternProperties,
          items = items,
          definitions = definitions,
          oneOf = oneOfs,
          ref = None)
    }
  }

  def getPath(ref: String): (String, Seq[String]) = {
    def splitJsonPointer(jsonPointer: String): Seq[String] = {
      jsonPointer.dropWhile(_ == '/').split("/")
    }

    ref.split('#') match {
      case Array(referredSchemaPath, jsonPointer) =>
        (referredSchemaPath, splitJsonPointer(jsonPointer))
      case Array(referredSchemaPath) =>
        (referredSchemaPath, Nil)
    }
  }
}

