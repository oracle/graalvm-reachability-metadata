/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.sangria_ast_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertSame, assertTrue}
import org.junit.jupiter.api.Test
import sangria.ast.*

class Sangria_ast_3Test {
  @Test
  def documentIndexesOperationsAndFragments(): Unit = {
    val idVariable: VariableDefinition = VariableDefinition(
      name = "viewerId",
      tpe = NotNullType(NamedType("ID")),
      defaultValue = Some(StringValue("current-user"))
    )
    val includeDirective: Directive = Directive(
      name = "include",
      arguments = Vector(Argument("if", BooleanValue(true)))
    )
    val avatarFragmentSpread: FragmentSpread = FragmentSpread(
      name = "AvatarFields",
      directives = Vector(Directive("defer"))
    )
    val viewerField: Field = Field(
      alias = Some("viewerAlias"),
      name = "viewer",
      arguments = Vector(Argument("id", VariableValue("viewerId"))),
      directives = Vector(includeDirective),
      selections = Vector(Field(None, "name", Vector.empty, Vector.empty, Vector.empty), avatarFragmentSpread)
    )
    val query: OperationDefinition = OperationDefinition(
      operationType = OperationType.Query,
      name = Some("GetViewer"),
      variables = Vector(idVariable),
      directives = Vector.empty,
      selections = Vector(viewerField)
    )
    val mutation: OperationDefinition = OperationDefinition(
      operationType = OperationType.Mutation,
      name = Some("RenameViewer"),
      variables = Vector.empty,
      directives = Vector.empty,
      selections = Vector(Field(None, "renameViewer", Vector(Argument("name", StringValue("Neo"))), Vector.empty, Vector.empty))
    )
    val fragment: FragmentDefinition = FragmentDefinition(
      name = "AvatarFields",
      typeCondition = NamedType("User"),
      directives = Vector.empty,
      selections = Vector(Field(None, "avatarUrl", Vector.empty, Vector.empty, Vector.empty))
    )
    val document: Document = Document(Vector(query, mutation, fragment))

    assertEquals(Some(query), document.operation(Some("GetViewer")))
    assertEquals(Some(OperationType.Mutation), document.operationType(Some("RenameViewer")))
    assertEquals(fragment, document.fragments("AvatarFields"))
    assertEquals("viewerAlias", viewerField.outputName)
    assertEquals(Some(NamedType("User")), fragment.typeConditionOpt)
    assertEquals(OperationType.Query, document.operations(Some("GetViewer")).operationType)
    assertFalse(document.source.isDefined)
  }

  @Test
  def astValuesAndTypesExposeRecursiveAndNameBasedViews(): Unit = {
    val itemType: Type = NotNullType(ListType(NotNullType(NamedType("String"))))
    val objectValue: ObjectValue = ObjectValue(Vector(
      ObjectField("limit", IntValue(25)),
      ObjectField("enabled", BooleanValue(true)),
      ObjectField("roles", ListValue(Vector(EnumValue("ADMIN"), EnumValue("USER")))),
      ObjectField("metadata", ObjectValue(Vector(ObjectField("empty", NullValue()))))
    ))
    val decimalValue: BigDecimalValue = BigDecimalValue(BigDecimal("1234.50"))
    val bigIntValue: BigIntValue = BigIntValue(BigInt("9223372036854775808"))
    val blockString: StringValue = StringValue("hello\nworld", block = true, blockRawValue = Some("\"\"\"hello\nworld\"\"\""))

    assertEquals(NamedType("String"), itemType.namedType)
    assertEquals(IntValue(25), objectValue.fieldsByName("limit"))
    assertEquals(BooleanValue(true), objectValue.fieldsByName("enabled"))
    assertEquals(ListValue(Vector(EnumValue("ADMIN"), EnumValue("USER"))), objectValue.fieldsByName("roles"))
    assertEquals(BigDecimal("1234.50"), decimalValue.value)
    assertEquals(BigInt("9223372036854775808"), bigIntValue.value)
    assertTrue(blockString.block)
    assertEquals(Some("\"\"\"hello\nworld\"\"\""), blockString.blockRawValue)
    assertEquals("value", objectValue.fields.head.productElementName(1))
  }

  @Test
  def schemaDefinitionsSupportDirectivesDescriptionsAndRenames(): Unit = {
    val description: StringValue = StringValue("A user visible in the API")
    val argumentDefinition: InputValueDefinition = InputValueDefinition(
      name = "first",
      valueType = NamedType("Int"),
      defaultValue = Some(IntValue(10)),
      directives = Vector(Directive("constraint", Vector(Argument("min", IntValue(1))))),
      description = Some(StringValue("Maximum number of records"))
    )
    val fieldDefinition: FieldDefinition = FieldDefinition(
      name = "friends",
      fieldType = ListType(NamedType("User")),
      arguments = Vector(argumentDefinition),
      directives = Vector(Directive("deprecated", Vector(Argument("reason", StringValue("Use connections")))))
    )
    val objectDefinition: ObjectTypeDefinition = ObjectTypeDefinition(
      name = "User",
      interfaces = Vector(NamedType("Node")),
      fields = Vector(fieldDefinition),
      directives = Vector(Directive("key", Vector(Argument("fields", StringValue("id"))))),
      description = Some(description)
    )
    val directiveDefinition: DirectiveDefinition = DirectiveDefinition(
      name = "cacheControl",
      arguments = Vector(InputValueDefinition("maxAge", NamedType("Int"), Some(IntValue(60)))),
      locations = Vector(DirectiveLocation("FIELD_DEFINITION"), DirectiveLocation("OBJECT")),
      description = Some(StringValue("Controls cache hints")),
      repeatable = true
    )
    val schema: SchemaDefinition = SchemaDefinition(
      operationTypes = Vector(OperationTypeDefinition(OperationType.Query, NamedType("Query"))),
      directives = Vector(Directive("link", Vector(Argument("url", StringValue("https://specs.apollo.dev/federation")))))
    )

    assertEquals("PublicUser", objectDefinition.rename("PublicUser").name)
    assertEquals(NamedType("User"), fieldDefinition.fieldType.namedType)
    assertEquals(Some(description), objectDefinition.description)
    assertEquals(Some(IntValue(10)), argumentDefinition.defaultValue)
    assertTrue(directiveDefinition.repeatable)
    assertEquals(Vector("FIELD_DEFINITION", "OBJECT"), directiveDefinition.locations.map(_.name))
    assertEquals(OperationType.Query, schema.operationTypes.head.operation)
  }

  @Test
  def typeSystemVariantsShareStableDefinitionBehavior(): Unit = {
    val idField: FieldDefinition = FieldDefinition("id", NotNullType(NamedType("ID")), Vector.empty)
    val nodeInterface: InterfaceTypeDefinition = InterfaceTypeDefinition(
      name = "Node",
      fields = Vector(idField),
      interfaces = Vector.empty,
      directives = Vector(Directive("interfaceDirective")),
      description = Some(StringValue("Global object identity"))
    )
    val searchUnion: UnionTypeDefinition = UnionTypeDefinition(
      name = "SearchResult",
      types = Vector(NamedType("User"), NamedType("Repository"))
    )
    val roleEnum: EnumTypeDefinition = EnumTypeDefinition(
      name = "Role",
      values = Vector(EnumValueDefinition("ADMIN"), EnumValueDefinition("USER"))
    )
    val inputObject: InputObjectTypeDefinition = InputObjectTypeDefinition(
      name = "UserFilter",
      fields = Vector(InputValueDefinition("active", NamedType("Boolean"), Some(BooleanValue(true))))
    )
    val dateScalar: ScalarTypeDefinition = ScalarTypeDefinition(
      name = "DateTime",
      directives = Vector(Directive("specifiedBy", Vector(Argument("url", StringValue("https://www.rfc-editor.org/rfc/rfc3339")))))
    )
    val definitions: Vector[Definition] = Vector(nodeInterface, searchUnion, roleEnum, inputObject, dateScalar)
    val document: Document = Document(definitions)

    assertEquals("NodeInterface", nodeInterface.rename("NodeInterface").name)
    assertEquals("SearchItem", searchUnion.rename("SearchItem").name)
    assertEquals("UserRole", roleEnum.rename("UserRole").name)
    assertEquals("UserFilterInput", inputObject.rename("UserFilterInput").name)
    assertEquals("Instant", dateScalar.rename("Instant").name)
    assertEquals(Vector("Node", "SearchResult", "Role", "UserFilter", "DateTime"), document.definitions.collect {
      case definition: TypeDefinition => definition.name
    })
  }

  @Test
  def sourceMappersRenderLocationsAndDocumentMergesPreserveSources(): Unit = {
    val firstInput: InMemorySourceMapperInput = InMemorySourceMapperInput("query GetViewer {\n  viewer { id }\n}")
    val secondInput: InMemorySourceMapperInput = InMemorySourceMapperInput("mutation RenameViewer {\n  renameViewer(name: \"Neo\") { id }\n}")
    val firstMapper: DefaultSourceMapper = new DefaultSourceMapper("query.graphql", firstInput)
    val secondMapper: DefaultSourceMapper = new DefaultSourceMapper("mutation.graphql", secondInput)
    val aggregateMapper: AggregateSourceMapper = AggregateSourceMapper.merge(Vector(firstMapper, secondMapper))
    val query: OperationDefinition = OperationDefinition(
      operationType = OperationType.Query,
      name = Some("GetViewer"),
      variables = Vector.empty,
      directives = Vector.empty,
      selections = Vector.empty,
      location = Some(AstLocation("query.graphql", 0, 1, 1))
    )
    val mutation: OperationDefinition = OperationDefinition(
      operationType = OperationType.Mutation,
      name = Some("RenameViewer"),
      variables = Vector.empty,
      directives = Vector.empty,
      selections = Vector.empty,
      location = Some(AstLocation("mutation.graphql", 0, 1, 1))
    )
    val firstDocument: Document = Document(Vector(query), sourceMapper = Some(firstMapper))
    val secondDocument: Document = Document(Vector(mutation), sourceMapper = Some(secondMapper))
    val mergedDocument: Document = firstDocument.merge(secondDocument)
    val renderedLocation: String = aggregateMapper.renderLocation(AstLocation("mutation.graphql", 24, 2, 3))
    val renderedMessage: String = aggregateMapper.renderLinePosition(AstLocation("query.graphql", 0, 1, 1), "query starts here")

    assertEquals(Vector(firstMapper, secondMapper), aggregateMapper.delegates)
    assertEquals("(line 2, column 3)", renderedLocation)
    assertTrue(renderedMessage.contains("query starts here"))
    assertEquals(Vector(query, mutation), mergedDocument.definitions)
    assertTrue(mergedDocument.source.exists(source => source.contains("GetViewer") && source.contains("RenameViewer")))
    assertFalse(mergedDocument.withoutSourceMapper.source.isDefined)
  }

  @Test
  def copyEqualityAndCacheKeysReactToAstContentChanges(): Unit = {
    val original: Field = Field(
      alias = Some("node"),
      name = "user",
      arguments = Vector(Argument("id", StringValue("123"))),
      directives = Vector.empty,
      selections = Vector(Field(None, "id", Vector.empty, Vector.empty, Vector.empty))
    )
    val changedArgument: Field = original.copy(arguments = Vector(Argument("id", StringValue("456"))))
    val renamedOutput: Field = original.copy(alias = Some("account"))
    val sameAgain: Field = original.copy()

    assertEquals(original, sameAgain)
    assertNotEquals(original, changedArgument)
    assertNotEquals(original.cacheKeyHash, changedArgument.cacheKeyHash)
    assertEquals("account", renamedOutput.outputName)
    assertSame(original.selections.head, sameAgain.selections.head)
  }
}

final case class InMemorySourceMapperInput(override val source: String) extends SourceMapperInput {
  private val lines: Vector[String] = source.split("\\R", -1).toVector

  override def getLine(line: Int): String = lines.lift(line - 1).getOrElse("")
}
