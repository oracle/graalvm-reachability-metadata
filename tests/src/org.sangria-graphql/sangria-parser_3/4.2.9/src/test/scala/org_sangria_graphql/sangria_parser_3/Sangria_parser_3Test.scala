/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.sangria_parser_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotNull, assertTrue, fail}
import org.junit.jupiter.api.Test
import sangria.ast.*
import sangria.parser.{ParserConfig, QueryParser, SyntaxError}

import scala.util.{Failure, Success, Try}

class Sangria_parser_3Test {
  @Test
  def parsesExecutableDocumentsWithOperationsFragmentsDirectivesAndSourceLocations(): Unit = {
    val query: String =
      """# operation comment
        |query GetUser($id: ID!, $withFriends: Boolean = true) @trace(enabled: true) {
        |  viewer: user(id: $id) {
        |    id
        |    name
        |    friends(first: 2) @include(if: $withFriends) {
        |      edges {
        |        node { ...UserFields }
        |      }
        |    }
        |    ... on Admin { permissions }
        |  }
        |}
        |
        |fragment UserFields on User {
        |  email
        |  profile { avatar(size: 64) }
        |}
        |""".stripMargin
    val document: Document = parseDocument(query, ParserConfig.default.withEmptySourceId)

    assertEquals(2, document.definitions.size)
    assertTrue(document.source.exists(_.contains("fragment UserFields")))
    assertEquals(Some(OperationType.Query), document.operationType(Some("GetUser")))
    assertTrue(document.location.exists(location => location.line == 1 && location.column == 1))

    val operation: OperationDefinition = document.definitions.head match {
      case parsed: OperationDefinition => parsed
      case other => fail(s"Expected an operation definition, got $other")
    }
    val fragment: FragmentDefinition = document.definitions(1) match {
      case parsed: FragmentDefinition => parsed
      case other => fail(s"Expected a fragment definition, got $other")
    }

    assertEquals("operation comment", operation.comments.head.text.trim)
    assertEquals(Some("GetUser"), operation.name)
    assertEquals(Vector("id", "withFriends"), operation.variables.map(_.name))
    assertTrue(operation.variables.head.tpe.isInstanceOf[NotNullType])
    assertEquals("ID", operation.variables.head.tpe.namedType.name)
    assertEquals(Some(true), operation.variables(1).defaultValue.collect { case value: BooleanValue => value.value })
    assertEquals("trace", operation.directives.head.name)
    assertTrue(operation.directives.head.arguments.head.value.asInstanceOf[BooleanValue].value)

    val viewer: Field = operation.selections.head match {
      case parsed: Field => parsed
      case other => fail(s"Expected a field selection, got $other")
    }
    assertEquals(Some("viewer"), viewer.alias)
    assertEquals("user", viewer.name)
    assertEquals("viewer", viewer.outputName)
    assertEquals("id", viewer.arguments.head.value.asInstanceOf[VariableValue].name)
    assertTrue(viewer.location.exists(location => location.line == 3 && location.column == 3))

    val friends: Field = viewer.selections.collectFirst { case field: Field if field.name == "friends" => field }.get
    assertEquals(BigInt(2), friends.arguments.head.value.asInstanceOf[BigIntValue].value)
    assertEquals("include", friends.directives.head.name)
    assertEquals("withFriends", friends.directives.head.arguments.head.value.asInstanceOf[VariableValue].name)
    assertTrue(viewer.selections.exists(_.isInstanceOf[InlineFragment]))

    assertEquals("UserFields", fragment.name)
    assertEquals("User", fragment.typeCondition.name)
    assertEquals(Vector("email", "profile"), fragment.selections.collect { case field: Field => field.name })
    assertTrue(document.fragments.contains("UserFields"))
  }

  @Test
  def parsesTypeSystemDefinitionsAndExtensions(): Unit = {
    val schemaDefinition: String =
      """schema @link(url: "https://specs.example/graphql") {
        |  query: Query
        |  mutation: Mutation
        |}
        |
        |"Authorisation metadata"
        |directive @auth(role: Role = ADMIN) repeatable on FIELD_DEFINITION | OBJECT
        |
        |"Custom instant"
        |scalar DateTime @specifiedBy(url: "https://www.rfc-editor.org/rfc/rfc3339")
        |
        |interface Node { id: ID! }
        |interface Resource implements Node { id: ID! url: String! }
        |
        |type Query implements Node & Resource @cache(ttl: 30) {
        |  id: ID!
        |  user(id: ID!, filter: UserFilter = { active: true }): User @auth(role: ADMIN)
        |}
        |
        |type User implements Node {
        |  id: ID!
        |  name: String!
        |  role: Role
        |}
        |
        |type Organization { id: ID! }
        |union SearchResult = User | Organization
        |enum Role { ADMIN USER }
        |input UserFilter { active: Boolean = true tags: [String!] }
        |
        |extend type User @key(fields: "id") { createdAt: DateTime }
        |extend schema @link(url: "https://specs.example/subscriptions") { subscription: Subscription }
        |""".stripMargin
    val document: Document = parseDocument(
      schemaDefinition,
      ParserConfig.default.withEmptySourceId.withoutSourceMapper.withoutLocations)

    assertEquals(13, document.definitions.size)
    val schema: SchemaDefinition = document.definitions.head match {
      case parsed: SchemaDefinition => parsed
      case other => fail(s"Expected a schema definition, got $other")
    }
    assertEquals(Vector(OperationType.Query, OperationType.Mutation), schema.operationTypes.map(_.operation))
    assertEquals("link", schema.directives.head.name)

    val authDirective: DirectiveDefinition = document.definitions(1) match {
      case parsed: DirectiveDefinition => parsed
      case other => fail(s"Expected a directive definition, got $other")
    }
    assertEquals(Some(StringValue("Authorisation metadata")), authDirective.description)
    assertTrue(authDirective.repeatable)
    assertEquals(Vector("FIELD_DEFINITION", "OBJECT"), authDirective.locations.map(_.name))
    assertEquals(Some(EnumValue("ADMIN")), authDirective.arguments.head.defaultValue)

    val dateTime: ScalarTypeDefinition = document.definitions.collectFirst {
      case parsed: ScalarTypeDefinition if parsed.name == "DateTime" => parsed
    }.get
    assertEquals(Some(StringValue("Custom instant")), dateTime.description)
    assertEquals("specifiedBy", dateTime.directives.head.name)

    val resource: InterfaceTypeDefinition = document.definitions.collectFirst {
      case parsed: InterfaceTypeDefinition if parsed.name == "Resource" => parsed
    }.get
    assertEquals(Vector("Node"), resource.interfaces.map(_.name))
    assertEquals(Vector("id", "url"), resource.fields.map(_.name))

    val query: ObjectTypeDefinition = document.definitions.collectFirst {
      case parsed: ObjectTypeDefinition if parsed.name == "Query" => parsed
    }.get
    assertEquals(Vector("Node", "Resource"), query.interfaces.map(_.name))
    assertEquals("cache", query.directives.head.name)
    val userField: FieldDefinition = query.fields.find(_.name == "user").get
    assertEquals(NamedType("User"), userField.fieldType)
    assertEquals(Vector("id", "filter"), userField.arguments.map(_.name))
    assertEquals(Some(ObjectValue(Vector(ObjectField("active", BooleanValue(true))))), userField.arguments(1).defaultValue)

    val searchResult: UnionTypeDefinition = document.definitions.collectFirst {
      case parsed: UnionTypeDefinition if parsed.name == "SearchResult" => parsed
    }.get
    assertEquals(Vector("User", "Organization"), searchResult.types.map(_.name))

    val role: EnumTypeDefinition = document.definitions.collectFirst {
      case parsed: EnumTypeDefinition if parsed.name == "Role" => parsed
    }.get
    assertEquals(Vector("ADMIN", "USER"), role.values.map(_.name))

    val filter: InputObjectTypeDefinition = document.definitions.collectFirst {
      case parsed: InputObjectTypeDefinition if parsed.name == "UserFilter" => parsed
    }.get
    assertEquals(Some(BooleanValue(true)), filter.fields.find(_.name == "active").get.defaultValue)
    assertEquals(ListType(NotNullType(NamedType("String"))), filter.fields.find(_.name == "tags").get.valueType)

    val userExtension: ObjectTypeExtensionDefinition = document.definitions.collectFirst {
      case parsed: ObjectTypeExtensionDefinition if parsed.name == "User" => parsed
    }.get
    assertEquals("key", userExtension.directives.head.name)
    assertEquals(Vector("createdAt"), userExtension.fields.map(_.name))

    val schemaExtension: SchemaExtensionDefinition = document.definitions.collectFirst {
      case parsed: SchemaExtensionDefinition => parsed
    }.get
    assertEquals(Vector(OperationType.Subscription), schemaExtension.operationTypes.map(_.operation))
  }

  @Test
  def parsesRemainingTypeSystemExtensionVariants(): Unit = {
    val extensions: String =
      """extend scalar DateTime @specifiedBy(url: "https://www.rfc-editor.org/rfc/rfc3339")
        |
        |extend interface NamedEntity implements Node @tag(name: "searchable") {
        |  displayName(locale: String = "en"): String!
        |}
        |
        |extend union SearchResult @tag(name: "extended") = Repository | Issue
        |
        |extend enum Role @tag(name: "security") {
        |  SUPER_ADMIN @deprecated(reason: "Use ADMIN")
        |}
        |
        |extend input UserFilter @tag(name: "filters") {
        |  search: String
        |  limit: Int = 10
        |}
        |""".stripMargin
    val document: Document = parseDocument(
      extensions,
      ParserConfig.default.withEmptySourceId.withoutSourceMapper.withoutLocations)

    assertEquals(5, document.definitions.size)

    val scalarExtension: ScalarTypeExtensionDefinition = document.definitions.collectFirst {
      case parsed: ScalarTypeExtensionDefinition => parsed
    }.get
    assertEquals("DateTime", scalarExtension.name)
    assertEquals("specifiedBy", scalarExtension.directives.head.name)
    assertEquals(
      "https://www.rfc-editor.org/rfc/rfc3339",
      scalarExtension.directives.head.arguments.head.value.asInstanceOf[StringValue].value)

    val interfaceExtension: InterfaceTypeExtensionDefinition = document.definitions.collectFirst {
      case parsed: InterfaceTypeExtensionDefinition => parsed
    }.get
    assertEquals("NamedEntity", interfaceExtension.name)
    assertEquals(Vector("Node"), interfaceExtension.interfaces.map(_.name))
    assertEquals("tag", interfaceExtension.directives.head.name)
    val displayName: FieldDefinition = interfaceExtension.fields.head
    assertEquals("displayName", displayName.name)
    assertEquals(NotNullType(NamedType("String")), displayName.fieldType)
    assertEquals(Vector("locale"), displayName.arguments.map(_.name))
    assertEquals(Some(StringValue("en")), displayName.arguments.head.defaultValue)

    val unionExtension: UnionTypeExtensionDefinition = document.definitions.collectFirst {
      case parsed: UnionTypeExtensionDefinition => parsed
    }.get
    assertEquals("SearchResult", unionExtension.name)
    assertEquals(Vector("Repository", "Issue"), unionExtension.types.map(_.name))
    assertEquals("extended", unionExtension.directives.head.arguments.head.value.asInstanceOf[StringValue].value)

    val enumExtension: EnumTypeExtensionDefinition = document.definitions.collectFirst {
      case parsed: EnumTypeExtensionDefinition => parsed
    }.get
    assertEquals("Role", enumExtension.name)
    assertEquals(Vector("SUPER_ADMIN"), enumExtension.values.map(_.name))
    assertEquals("deprecated", enumExtension.values.head.directives.head.name)
    assertEquals(
      "Use ADMIN",
      enumExtension.values.head.directives.head.arguments.head.value.asInstanceOf[StringValue].value)

    val inputExtension: InputObjectTypeExtensionDefinition = document.definitions.collectFirst {
      case parsed: InputObjectTypeExtensionDefinition => parsed
    }.get
    assertEquals("UserFilter", inputExtension.name)
    assertEquals(Vector("search", "limit"), inputExtension.fields.map(_.name))
    assertEquals(NamedType("String"), inputExtension.fields.head.valueType)
    assertEquals(Some(BigIntValue(BigInt(10))), inputExtension.fields(1).defaultValue)
    assertEquals("filters", inputExtension.directives.head.arguments.head.value.asInstanceOf[StringValue].value)
  }

  @Test
  def parsesStandaloneInputDocumentsWithScalarsListsObjectsVariablesAndTrailingComments(): Unit = {
    val input: String = (
      """{
        |  name: "Ada\nLovelace"
        |  bio: """.stripMargin + "\"\"\"" + """
        |    first
        |      programmer
        |  """.stripMargin + "\"\"\"" + """
        |  count: 9223372036854775808
        |  ratio: -12.5e2
        |  flags: [true, false, null, ENUM]
        |  variable: $id
        |}
        |[1, 2, 3]
        |# trailing input comment
        |""".stripMargin)
    val document: InputDocument = parseInputDocumentWithVariables(input)

    assertEquals(2, document.values.size)
    assertEquals("trailing input comment", document.trailingComments.head.text.trim)
    assertTrue(document.source.exists(_.contains("Ada\\nLovelace")))

    val profile: ObjectValue = document.values.head match {
      case parsed: ObjectValue => parsed
      case other => fail(s"Expected an object value, got $other")
    }
    assertEquals("Ada\nLovelace", profile.fieldsByName("name").asInstanceOf[StringValue].value)
    val bio: StringValue = profile.fieldsByName("bio") match {
      case parsed: StringValue => parsed
      case other => fail(s"Expected a string value, got $other")
    }
    assertTrue(bio.block)
    assertTrue(bio.value.contains("first"))
    assertTrue(bio.value.contains("programmer"))
    assertEquals(BigInt("9223372036854775808"), profile.fieldsByName("count").asInstanceOf[BigIntValue].value)
    assertEquals(BigDecimal("-12.5e2"), profile.fieldsByName("ratio").asInstanceOf[BigDecimalValue].value)
    assertEquals("id", profile.fieldsByName("variable").asInstanceOf[VariableValue].name)

    val flags: ListValue = profile.fieldsByName("flags") match {
      case parsed: ListValue => parsed
      case other => fail(s"Expected a list value, got $other")
    }
    assertEquals(Vector(true, false), flags.values.take(2).map(_.asInstanceOf[BooleanValue].value))
    assertTrue(flags.values(2).isInstanceOf[NullValue])
    assertEquals("ENUM", flags.values(3).asInstanceOf[EnumValue].value)
    assertEquals(Vector(BigInt(1), BigInt(2), BigInt(3)), document.values(1).asInstanceOf[ListValue].values.map(_.asInstanceOf[BigIntValue].value))
    assertEquals("hello", QueryParser.parseInput("\"hello\"").get.asInstanceOf[StringValue].value)
  }

  @Test
  def parserConfigControlsCommentsLocationsSourceIdsAndFragmentVariables(): Unit = {
    val query: String =
      """# hidden comment
        |query NoMetadata {
        |  field
        |}
        |""".stripMargin
    val metadataFree: Document = parseDocument(
      query,
      ParserConfig.default.withEmptySourceId.withoutSourceMapper.withoutLocations.withoutComments)
    val operationWithoutMetadata: OperationDefinition = metadataFree.definitions.head.asInstanceOf[OperationDefinition]
    val fieldWithoutMetadata: Field = operationWithoutMetadata.selections.head.asInstanceOf[Field]

    assertFalse(metadataFree.location.isDefined)
    assertTrue(operationWithoutMetadata.comments.isEmpty)
    assertTrue(operationWithoutMetadata.location.isEmpty)
    assertTrue(fieldWithoutMetadata.location.isEmpty)
    assertTrue(fieldWithoutMetadata.comments.isEmpty)

    val sourceId: String = "stable-source.graphql"
    val withSourceId: Document = parseDocument(
      "query Located { field }",
      ParserConfig.default.copy(sourceIdFn = _ => sourceId))
    val locatedOperation: OperationDefinition = withSourceId.definitions.head.asInstanceOf[OperationDefinition]
    assertEquals(Some(sourceId), locatedOperation.location.map(_.sourceId))
    assertTrue(withSourceId.source.exists(_.contains("Located")))

    val fragmentWithVariables: String =
      """fragment Avatar($size: Int = 64) on User {
        |  avatar(size: $size)
        |}
        |""".stripMargin
    assertSyntaxError(QueryParser.parse(fragmentWithVariables, ParserConfig.default.withEmptySourceId.withoutSourceMapper))

    val parsed: Document = parseDocument(
      fragmentWithVariables,
      ParserConfig.default.withEmptySourceId.withoutSourceMapper.withExperimentalFragmentVariables)
    val fragment: FragmentDefinition = parsed.definitions.head.asInstanceOf[FragmentDefinition]
    assertEquals(Vector("size"), fragment.variables.map(_.name))
    assertEquals(Some(BigInt(64)), fragment.variables.head.defaultValue.collect { case value: BigIntValue => value.value })
  }

  @Test
  def sourceMapperRendersLinePositionForParsedNodes(): Unit = {
    val sourceId: String = "source-mapper.graphql"
    val query: String =
      """query LocateField {
        |  user {
        |    name
        |  }
        |}
        |""".stripMargin
    val document: Document = parseDocument(query, ParserConfig.default.copy(sourceIdFn = _ => sourceId))
    val sourceMapper: SourceMapper = document.sourceMapper.getOrElse(fail("Expected a source mapper"))
    val operation: OperationDefinition = document.operation(Some("LocateField")).get
    val user: Field = operation.selections.head.asInstanceOf[Field]
    val name: Field = user.selections.head.asInstanceOf[Field]
    val location: AstLocation = name.location.getOrElse(fail("Expected field location"))

    assertEquals(sourceId, sourceMapper.id)
    assertEquals(query, sourceMapper.source)
    assertEquals("(line 3, column 5)", sourceMapper.renderLocation(location))
    val renderedLine: String = sourceMapper.renderLinePosition(location, "GraphQL: ")
    assertTrue(renderedLine.contains("    name"))
    assertTrue(renderedLine.contains("GraphQL:     ^"))
  }

  @Test
  def reportsSyntaxErrorsForInvalidDocumentsAndInputs(): Unit = {
    val syntaxError: SyntaxError = assertSyntaxError(
      QueryParser.parse("{ field: {} }", ParserConfig.default.withEmptySourceId.withoutSourceMapper))

    assertTrue(syntaxError.formattedError.contains("expected"))
    assertTrue(syntaxError.formattedError.contains("line 1, column 1"))
    assertFalse(syntaxError.formattedError(false).contains("^"))
    assertTrue(syntaxError.getMessage.contains("Syntax error while parsing GraphQL query"))
    assertNotNull(syntaxError.originalError)

    val inputSyntaxError: SyntaxError = assertSyntaxError(QueryParser.parseInput(""))
    assertTrue(inputSyntaxError.formattedError.contains("expected"))
  }

  private def parseDocument(input: String, config: ParserConfig = ParserConfig.default.withEmptySourceId.withoutSourceMapper): Document =
    QueryParser.parse(input, config).get

  private def parseInputDocumentWithVariables(input: String): InputDocument =
    QueryParser.parseInputDocumentWithVariables(input, ParserConfig.default.withEmptySourceId).get


  private def assertSyntaxError(result: Try[?]): SyntaxError =
    result match {
      case Failure(error: SyntaxError) => error
      case Failure(error) => fail(s"Expected SyntaxError, got $error")
      case Success(value) => fail(s"Expected failure, got success with $value")
    }
}
