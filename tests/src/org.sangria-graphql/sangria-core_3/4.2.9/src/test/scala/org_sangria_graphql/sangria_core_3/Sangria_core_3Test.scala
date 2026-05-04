/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.sangria_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue, fail}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import sangria.ast.{BooleanValue, Document, EnumTypeDefinition, EnumValue as AstEnumValue, ListValue, ObjectTypeDefinition, ObjectValue, StringValue, Value}
import sangria.execution.{Executor, MaxQueryDepthReachedError, QueryReducer, QueryReducingError, ValidationError}
import sangria.marshalling.{InputUnmarshaller, ScalaInput}
import sangria.marshalling.queryAst.queryAstResultMarshaller
import sangria.renderer.SchemaFilter
import sangria.parser.QueryParser
import sangria.schema.{Argument, EnumType, EnumValue as SchemaEnumValue, Field, IDType, IntType, ListType, Named, ObjectType, OptionInputType, OptionType, Schema, StringType, UnionType, fields}

import sangria.util.tag.@@

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

class Sangria_core_3Test {
  private given ExecutionContext = ExecutionContext.global
  private given InputUnmarshaller[Any @@ ScalaInput] = InputUnmarshaller.scalaInputUnmarshaller[Any]

  private enum Episode:
    case NewHope, Empire, Jedi

  private sealed trait SearchResult:
    def id: String
    def name: String

  private final case class Human(id: String, name: String, homePlanet: Option[String], appearsIn: Vector[Episode])
      extends SearchResult

  private final case class Droid(id: String, name: String, primaryFunction: String, appearsIn: Vector[Episode])
      extends SearchResult

  private val Luke: Human = Human("1000", "Luke Skywalker", Some("Tatooine"), Vector(Episode.NewHope, Episode.Empire, Episode.Jedi))
  private val Leia: Human = Human("1003", "Leia Organa", Some("Alderaan"), Vector(Episode.NewHope, Episode.Empire, Episode.Jedi))
  private val R2D2: Droid = Droid("2001", "R2-D2", "Astromech", Vector(Episode.NewHope, Episode.Empire, Episode.Jedi))
  private val C3PO: Droid = Droid("2000", "C-3PO", "Protocol", Vector(Episode.NewHope, Episode.Empire, Episode.Jedi))
  private val Characters: List[SearchResult] = List(Luke, R2D2, Leia, C3PO)

  private val EpisodeEnum: EnumType[Episode] = EnumType(
    "Episode",
    Some("One of the original trilogy episodes."),
    List(
      SchemaEnumValue("NEWHOPE", value = Episode.NewHope, description = Some("Released in 1977.")),
      SchemaEnumValue("EMPIRE", value = Episode.Empire, description = Some("Released in 1980.")),
      SchemaEnumValue("JEDI", value = Episode.Jedi, description = Some("Released in 1983."))
    )
  )

  private val EpisodeArg: Argument[Option[Episode]] = Argument("episode", OptionInputType(EpisodeEnum))
  private val LimitArg: Argument[Option[Int]] = Argument("limit", OptionInputType(IntType))
  private val TextArg: Argument[String] = Argument("text", StringType)
  private val NameArg: Argument[String] = Argument("name", StringType)
  private val TimesArg: Argument[Int] = Argument("times", IntType)

  private lazy val HumanType: ObjectType[Unit, Human] = ObjectType(
    "Human",
    "A human character.",
    fields[Unit, Human](
      Field("id", IDType, resolve = context => context.value.id),
      Field("name", StringType, resolve = context => context.value.name),
      Field("legacyName", OptionType(StringType), deprecationReason = Some("Use name instead."), resolve = context => Some(context.value.name)),
      Field("homePlanet", OptionType(StringType), resolve = context => context.value.homePlanet),
      Field("appearsIn", ListType(EpisodeEnum), resolve = context => context.value.appearsIn)
    )
  )

  private lazy val DroidType: ObjectType[Unit, Droid] = ObjectType(
    "Droid",
    "A mechanical character.",
    fields[Unit, Droid](
      Field("id", IDType, resolve = context => context.value.id),
      Field("name", StringType, resolve = context => context.value.name),
      Field("primaryFunction", StringType, resolve = context => context.value.primaryFunction),
      Field("appearsIn", ListType(EpisodeEnum), resolve = context => context.value.appearsIn)
    )
  )

  private lazy val SearchResultType: UnionType[Unit] = UnionType(
    "SearchResult",
    Some("A character returned by search."),
    List(HumanType, DroidType)
  )

  private lazy val QueryType: ObjectType[Unit, Unit] = ObjectType(
    "Query",
    fields[Unit, Unit](
      Field(
        "hero",
        SearchResultType,
        arguments = EpisodeArg :: Nil,
        resolve = context => heroFor(context.arg(EpisodeArg).getOrElse(Episode.NewHope))
      ),
      Field(
        "search",
        ListType(SearchResultType),
        arguments = TextArg :: LimitArg :: Nil,
        resolve = context => search(context.arg(TextArg), context.arg(LimitArg))
      ),
      Field(
        "repeat",
        StringType,
        arguments = TextArg :: TimesArg :: Nil,
        resolve = context => Vector.fill(context.arg(TimesArg))(context.arg(TextArg)).mkString("-")
      )
    )
  )

  private lazy val MutationType: ObjectType[Unit, Unit] = ObjectType(
    "Mutation",
    fields[Unit, Unit](
      Field(
        "renameHero",
        HumanType,
        arguments = NameArg :: Nil,
        resolve = context => Luke.copy(name = context.arg(NameArg))
      ),
      Field(
        "repeat",
        StringType,
        arguments = TextArg :: TimesArg :: Nil,
        resolve = context => Vector.fill(context.arg(TimesArg))(context.arg(TextArg)).mkString("-")
      )
    )
  )

  private lazy val CharacterSchema: Schema[Unit, Unit] = Schema(QueryType, mutation = Some(MutationType))

  @Test
  def executesQueryWithUnionFragmentsAliasesDirectivesEnumsAndVariables(): Unit = {
    val result: ObjectValue = execute(
      """
        |query CharacterOverview($includePlanet: Boolean!) {
        |  hero(episode: EMPIRE) {
        |    __typename
        |    ... on Human {
        |      id
        |      fullName: name
        |      homePlanet @include(if: $includePlanet)
        |      appearsIn
        |    }
        |  }
        |  search(text: "R", limit: 2) {
        |    __typename
        |    ... on Human { name }
        |    ... on Droid { name primaryFunction }
        |  }
        |}
        |""".stripMargin,
      Map("includePlanet" -> true)
    )

    val data: ObjectValue = objectField(result, "data")
    val hero: ObjectValue = objectField(data, "hero")
    assertStringField(hero, "__typename", "Human")
    assertStringField(hero, "id", "1000")
    assertStringField(hero, "fullName", "Luke Skywalker")
    assertStringField(hero, "homePlanet", "Tatooine")
    assertEquals(Vector("NEWHOPE", "EMPIRE", "JEDI"), listField(hero, "appearsIn").values.map(_.asInstanceOf[AstEnumValue].value))

    val matches: Vector[Value] = listField(data, "search").values
    assertEquals(2, matches.size)
    val firstMatch: ObjectValue = matches.head.asInstanceOf[ObjectValue]
    val secondMatch: ObjectValue = matches(1).asInstanceOf[ObjectValue]
    assertStringField(firstMatch, "__typename", "Human")
    assertStringField(firstMatch, "name", "Luke Skywalker")
    assertStringField(secondMatch, "__typename", "Droid")
    assertStringField(secondMatch, "name", "R2-D2")
    assertStringField(secondMatch, "primaryFunction", "Astromech")
  }

  @Test
  def executesMutationAndCoercesScalarVariables(): Unit = {
    val result: ObjectValue = execute(
      """
        |mutation RenameAndRepeat($newName: String!, $times: Int!) {
        |  renameHero(name: $newName) { id name }
        |  repeat(text: "ha", times: $times)
        |}
        |""".stripMargin,
      Map("newName" -> "General Organa", "times" -> 3)
    )

    val data: ObjectValue = objectField(result, "data")
    val renamed: ObjectValue = objectField(data, "renameHero")
    assertStringField(renamed, "id", "1000")
    assertStringField(renamed, "name", "General Organa")
    assertStringField(data, "repeat", "ha-ha-ha")
  }

  @Test
  def reportsValidationErrorsBeforeExecution(): Unit = {
    val error: ValidationError = org.junit.jupiter.api.Assertions.assertThrows(
      classOf[ValidationError],
      new Executable {
        override def execute(): Unit = {
          Await.result(
            Executor.execute(CharacterSchema, parse("{ hero { missingField } }"), variables = InputUnmarshaller.emptyMapVars),
            5.seconds
          )
        }
      }
    )

    assertFalse(error.violations.isEmpty)
    assertTrue(error.violations.exists(_.errorMessage.contains("Cannot query field")))
    assertTrue(error.beforeExecution)
  }

  @Test
  def rendersSchemaBuildsAstAndExposesTypeMetadata(): Unit = {
    val rendered: String = CharacterSchema.renderPretty(SchemaFilter.default)

    assertTrue(rendered.contains("enum Episode"))
    assertTrue(rendered.contains("union SearchResult = Human | Droid"))
    assertTrue(rendered.contains("type Mutation"))
    assertTrue(rendered.contains("legacyName: String @deprecated(reason: \"Use name instead.\")"))
    assertTrue(CharacterSchema.availableTypeNames.contains("Human"))
    assertTrue(CharacterSchema.availableTypeNames.contains("Droid"))
    assertTrue(CharacterSchema.outputTypes.contains("SearchResult"))
    assertTrue(CharacterSchema.directivesByName.contains("include"))
    assertTrue(Schema.getBuiltInType("String").isDefined)
    assertTrue(Schema.isBuiltInDirective("skip"))
    assertFalse(Schema.isBuiltInType("SearchResult"))

    val ast: Document = CharacterSchema.toAst(SchemaFilter.default)
    assertTrue(ast.definitions.exists {
      case definition: ObjectTypeDefinition => definition.name == "Human"
      case _ => false
    })
    assertTrue(ast.definitions.exists {
      case definition: EnumTypeDefinition => definition.name == "Episode"
      case _ => false
    })

    val stubSource: Document = parse(
      """
        |type Query {
        |  greeting: String
        |}
        |
        |enum Episode {
        |  NEWHOPE
        |  EMPIRE
        |  JEDI
        |}
        |""".stripMargin
    )
    val definitions: Vector[Named] = Schema.buildDefinitions(stubSource)
    assertTrue(definitions.exists(_.name == "Episode"))
    assertTrue(definitions.exists(_.name == "Query"))
  }

  @Test
  def executesIntrospectionQueriesForSchemaAndDeprecatedFields(): Unit = {
    val result: ObjectValue = execute(
      """
        |{
        |  __schema {
        |    queryType { name }
        |    mutationType { name }
        |    types { name }
        |    directives { name }
        |  }
        |  __type(name: "Human") {
        |    name
        |    kind
        |    fields(includeDeprecated: true) {
        |      name
        |      isDeprecated
        |      deprecationReason
        |    }
        |  }
        |}
        |""".stripMargin
    )

    val data: ObjectValue = objectField(result, "data")
    val schema: ObjectValue = objectField(data, "__schema")
    assertStringField(objectField(schema, "queryType"), "name", "Query")
    assertStringField(objectField(schema, "mutationType"), "name", "Mutation")
    assertTrue(listField(schema, "types").values.exists {
      case objectValue: ObjectValue => objectValue.fieldsByName("name") == StringValue("SearchResult")
      case _ => false
    })
    assertTrue(listField(schema, "directives").values.exists {
      case objectValue: ObjectValue => objectValue.fieldsByName("name") == StringValue("include")
      case _ => false
    })

    val humanType: ObjectValue = objectField(data, "__type")
    assertStringField(humanType, "name", "Human")
    assertStringField(humanType, "kind", "OBJECT")

    val fields: Vector[Value] = listField(humanType, "fields").values
    val legacyName: ObjectValue = fields.collectFirst {
      case objectValue: ObjectValue if objectValue.fieldsByName("name") == StringValue("legacyName") => objectValue
    }.getOrElse(fail("Expected introspection result to include deprecated legacyName field"))
    assertBooleanField(legacyName, "isDeprecated", expected = true)
    assertStringField(legacyName, "deprecationReason", "Use name instead.")
  }

  @Test
  def coercesEnumValuesAndBuiltInScalarsThroughPublicTypeApi(): Unit = {
    assertEquals(Right(Episode.Empire), EpisodeEnum.coerceInput(AstEnumValue("EMPIRE")).map(_._1))
    assertEquals(Right(Episode.Jedi), EpisodeEnum.coerceUserInput("JEDI").map(_._1))
    assertTrue(EpisodeEnum.coerceInput(StringValue("EMPIRE")).isLeft)
    assertTrue(EpisodeEnum.coerceUserInput("UNKNOWN").isLeft)

    assertEquals(Right("abc"), StringType.coerceInput(StringValue("abc")))
    assertTrue(IntType.coerceInput(StringValue("not an int")).isLeft)
  }

  @Test
  def rejectsQueriesThatExceedConfiguredMaximumDepth(): Unit = {
    val error: QueryReducingError = org.junit.jupiter.api.Assertions.assertThrows(
      classOf[QueryReducingError],
      new Executable {
        override def execute(): Unit = {
          Await.result(
            Executor.execute(
              CharacterSchema,
              parse(
                """
                  |{
                  |  hero {
                  |    ... on Human {
                  |      id
                  |    }
                  |    ... on Droid {
                  |      id
                  |    }
                  |  }
                  |}
                  |""".stripMargin
              ),
              variables = InputUnmarshaller.emptyMapVars,
              queryReducers = QueryReducer.rejectMaxDepth[Unit](1) :: Nil
            ),
            5.seconds
          )
        }
      }
    )

    assertTrue(error.beforeExecution)
    error.cause match {
      case maxDepthError: MaxQueryDepthReachedError => assertEquals(1, maxDepthError.maxDepth)
      case other => fail(s"Expected max-depth rejection, got $other")
    }
  }

  private def heroFor(episode: Episode): SearchResult =
    episode match {
      case Episode.Empire => Luke
      case Episode.NewHope => R2D2
      case Episode.Jedi => Leia
    }

  private def search(text: String, limit: Option[Int]): List[SearchResult] = {
    val lowered: String = text.toLowerCase
    Characters.filter(_.name.toLowerCase.contains(lowered)).take(limit.getOrElse(Characters.size))
  }

  private def execute(query: String, variables: Map[String, Any] = Map.empty): ObjectValue = {
    val graphQlVariables: Any @@ ScalaInput = InputUnmarshaller.mapVars(variables)
    val result: Value = Await.result(
      Executor.execute(CharacterSchema, parse(query), variables = graphQlVariables),
      5.seconds
    )

    result match {
      case objectValue: ObjectValue => objectValue
      case other => fail(s"Expected GraphQL execution result object, got $other")
    }
  }

  private def parse(query: String): Document =
    QueryParser.parse(query).get

  private def objectField(value: ObjectValue, fieldName: String): ObjectValue =
    value.fieldsByName(fieldName) match {
      case objectValue: ObjectValue => objectValue
      case other => fail(s"Expected field '$fieldName' to be an object, got $other")
    }

  private def listField(value: ObjectValue, fieldName: String): ListValue =
    value.fieldsByName(fieldName) match {
      case listValue: ListValue => listValue
      case other => fail(s"Expected field '$fieldName' to be a list, got $other")
    }

  private def assertStringField(value: ObjectValue, fieldName: String, expected: String): Unit =
    value.fieldsByName(fieldName) match {
      case actual: StringValue => assertEquals(expected, actual.value)
      case actual: AstEnumValue => assertEquals(expected, actual.value)
      case other => fail(s"Expected field '$fieldName' to be a string-like value, got $other")
    }

  private def assertBooleanField(value: ObjectValue, fieldName: String, expected: Boolean): Unit =
    value.fieldsByName(fieldName) match {
      case actual: BooleanValue => assertEquals(expected, actual.value)
      case other => fail(s"Expected field '$fieldName' to be a boolean value, got $other")
    }
}
