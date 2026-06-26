/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.sangria_marshalling_api_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertNull, assertSame, assertThrows, assertTrue}
import org.junit.jupiter.api.Test
import sangria.marshalling.*
import sangria.marshalling.MarshallingUtil.ResultMarshallerOps
import sangria.util.tag.@@

import scala.collection.immutable.ListMap
import scala.util.Try

class Sangria_marshalling_api_3Test {
  @Test
  def arrayMapBuilderPreservesDeclaredFieldOrderAndExposesCollectionViews(): Unit = {
    val builder: ArrayMapBuilder[Int] = new ArrayMapBuilder[Int](Seq("id", "name", "active"))
      .add("active", 1)
      .add("id", 2)
      .add("active", 3)

    val expectedEntries: Seq[(String, Int)] = Seq("id" -> 2, "active" -> 3)
    assertEquals(expectedEntries.toList, builder.toList)
    assertEquals(expectedEntries.toVector, builder.toVector)
    assertEquals(expectedEntries, builder.toSeq)
    assertEquals(ListMap("id" -> 2, "active" -> 3), builder.toListMap)
    assertEquals(Map("id" -> 2, "active" -> 3), builder.toMap)
    assertTrue(builder.exists(_ == ("active" -> 3)))
    assertFalse(builder.exists(_._1 == "name"))

    val exception: NoSuchElementException = assertThrows(
      classOf[NoSuchElementException],
      () => builder.add("unknown", 4)
    )
    assertEquals("key not found: unknown", exception.getMessage)
  }

  @Test
  def scalaResultMarshallerBuildsScalarListMapAndOptionalNodes(): Unit = {
    val marshaller: ScalaResultMarshaller = new ScalaResultMarshaller
    val builder: marshaller.MapBuilder = marshaller.emptyMapNode(Seq("second", "first", "third"))
    val withFirst: marshaller.MapBuilder = marshaller.addMapNodeElem(builder, "first", 1, optional = false)
    val withBoth: marshaller.MapBuilder = marshaller.addMapNodeElem(withFirst, "second", "two", optional = true)

    assertEquals("value", marshaller.scalarNode("value", "String", Set.empty))
    assertEquals("ADMIN", marshaller.enumNode("ADMIN", "Role"))
    assertEquals(Vector(1, "two", true), marshaller.arrayNode(Vector(1, "two", true)))
    assertEquals("present", marshaller.optionalArrayNodeValue(Some("present")))
    assertNull(marshaller.optionalArrayNodeValue(None))
    assertNull(marshaller.nullNode)
    assertEquals(ListMap("second" -> "two", "first" -> 1), marshaller.mapNode(withBoth))
    assertEquals(ListMap("name" -> "Ada", "score" -> 99), marshaller.mapNode(Seq("name" -> "Ada", "score" -> 99)))
    assertEquals("ListMap(name -> Ada, score -> 99)", marshaller.renderCompact(ListMap("name" -> "Ada", "score" -> 99)))
    assertEquals("42", marshaller.renderPretty(42))
    assertEquals(Set.empty[MarshallerCapability], marshaller.capabilities)
  }

  @Test
  def coercedScalaMarshallerKeepsRawValuesAndWrapsOptionalMapMembers(): Unit = {
    val marshaller: CoercedScalaResultMarshaller = CoercedScalaResultMarshaller.default
    val builder: marshaller.MapBuilder = marshaller.emptyMapNode(Seq("required", "optional", "missing"))
    val withRequired: marshaller.MapBuilder = marshaller.addMapNodeElem(builder, "required", "raw", optional = false)
    val withOptional: marshaller.MapBuilder = marshaller.addMapNodeElem(withRequired, "optional", 10, optional = true)
    val complete: marshaller.MapBuilder = marshaller.addMapNodeElem(withOptional, "missing", None, optional = true)

    assertEquals("raw", marshaller.rawScalarNode("raw"))
    assertEquals(Vector("a", 2), marshaller.arrayNode(Vector("a", 2)))
    assertEquals(Some("element"), marshaller.optionalArrayNodeValue(Some("element")))
    assertEquals(None, marshaller.optionalArrayNodeValue(None))
    assertEquals(None, marshaller.nullNode)
    assertEquals(ListMap("required" -> "raw", "optional" -> Some(10), "missing" -> None), marshaller.mapNode(complete))
    assertEquals(ListMap("direct" -> true), marshaller.mapNode(Seq("direct" -> true)))
    assertEquals("Some(value)", marshaller.renderPretty(Some("value")))

    val scalarException: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => marshaller.scalarNode("not-raw", "String", Set.empty)
    )
    assertEquals("Only raw values expected in `RawResultMarshaller`!", scalarException.getMessage)
    assertThrows(classOf[IllegalArgumentException], () => marshaller.enumNode("ADMIN", "Role"))
  }

  @Test
  def scalaInputUnmarshallerClassifiesMapsListsScalarsNullsAndUnsupportedVariables(): Unit = {
    val unmarshaller: InputUnmarshaller[Any @@ ScalaInput] = InputUnmarshaller.scalaInputUnmarshaller[Any]
    val node: Any @@ ScalaInput = ScalaInput.scalaInput(ListMap(
      "profile" -> ListMap("name" -> "Ada", "age" -> 37),
      "roles" -> Vector("ADMIN", "USER"),
      "active" -> true,
      "empty" -> null
    ))

    assertTrue(unmarshaller.isMapNode(node))
    assertEquals(List("profile", "roles", "active", "empty"), unmarshaller.getMapKeys(node).toList)
    assertEquals(unmarshaller.getMapValue(node, "profile"), unmarshaller.getRootMapValue(node, "profile"))
    assertEquals(None, unmarshaller.getMapValue(node, "missing"))

    val profile: Any @@ ScalaInput = unmarshaller.getMapValue(node, "profile").get
    assertTrue(unmarshaller.isMapNode(profile))
    assertEquals(Some("Ada"), unmarshaller.getMapValue(profile, "name"))

    val roles: Any @@ ScalaInput = unmarshaller.getMapValue(node, "roles").get
    assertTrue(unmarshaller.isListNode(roles))
    assertEquals(Vector("ADMIN", "USER"), unmarshaller.getListValue(roles).toVector)

    val active: Any @@ ScalaInput = unmarshaller.getMapValue(node, "active").get
    assertTrue(unmarshaller.isScalarNode(active))
    assertTrue(unmarshaller.isEnumNode(active))
    assertEquals(true, unmarshaller.getScalarValue(active))
    assertEquals(true, unmarshaller.getScalaScalarValue(active))
    assertEquals("true", unmarshaller.render(active))

    val empty: Any @@ ScalaInput = unmarshaller.getMapValue(node, "empty").getOrElse(null)
    assertFalse(unmarshaller.isDefined(empty))
    assertEquals("null", unmarshaller.render(empty))
    assertFalse(unmarshaller.isVariableNode(active))
    assertThrows(classOf[IllegalArgumentException], () => unmarshaller.getVariableName(active))
  }

  @Test
  def inputUnmarshallerVariableMapFactoriesCreateTaggedScalaInputs(): Unit = {
    val unmarshaller: InputUnmarshaller[Any @@ ScalaInput] = InputUnmarshaller.scalaInputUnmarshaller[Any]
    val variablesFromPairs: Any @@ ScalaInput = InputUnmarshaller.mapVars(
      "id" -> 42,
      "name" -> "Ada"
    )
    val variablesFromMap: Any @@ ScalaInput = InputUnmarshaller.mapVars(Map[String, Any](
      "enabled" -> true,
      "limit" -> 10
    ))
    val emptyVariables: Any @@ ScalaInput = InputUnmarshaller.emptyMapVars

    assertTrue(unmarshaller.isMapNode(variablesFromPairs))
    assertEquals(Set("id", "name"), unmarshaller.getMapKeys(variablesFromPairs).toSet)
    assertEquals(Some(42), unmarshaller.getRootMapValue(variablesFromPairs, "id"))
    assertEquals(Some("Ada"), unmarshaller.getRootMapValue(variablesFromPairs, "name"))
    assertEquals(None, unmarshaller.getRootMapValue(variablesFromPairs, "missing"))

    assertTrue(unmarshaller.isMapNode(variablesFromMap))
    assertEquals(Some(true), unmarshaller.getRootMapValue(variablesFromMap, "enabled"))
    assertEquals(Some(10), unmarshaller.getRootMapValue(variablesFromMap, "limit"))

    assertTrue(unmarshaller.isMapNode(emptyVariables))
    assertEquals(Nil, unmarshaller.getMapKeys(emptyVariables).toList)
    assertEquals(None, unmarshaller.getRootMapValue(emptyVariables, "id"))
  }

  @Test
  def toInputInstancesExposeScalarsThroughScalaInputUnmarshaller(): Unit = {
    assertScalarToInput(ToInput.intInput, 42)
    assertScalarToInput(ToInput.longInput, 42L)
    assertScalarToInput(ToInput.floatInput, 2.5d)
    assertScalarToInput(ToInput.booleanInput, true)
    assertScalarToInput(ToInput.stringInput, "native")
    assertScalarToInput(ToInput.bigIntInput, BigInt("12345678901234567890"))
    assertScalarToInput(ToInput.bigDecimalInput, BigDecimal("12345.6789"))

    val taggedList: List[Int] @@ ScalaInput = ScalaInput.scalaInput(List(1, 2, 3))
    val (rawList, listUnmarshaller) = ToInput.normalScalaInput[List[Int]].toInput(taggedList)
    assertEquals(List(1, 2, 3), listUnmarshaller.getListValue(rawList).toList)
  }

  @Test
  def fromInputInstancesReadCoercedScalarOptionalSequenceAndMapResults(): Unit = {
    val scalarInput: FromInput[String @@ FromInput.CoercedScalaResult] = FromInput.coercedScalaInput[String]
    assertEquals("scalar", scalarInput.fromResult("scalar".asInstanceOf[scalarInput.marshaller.Node]))
    assertSame(CoercedScalaResultMarshaller.default, scalarInput.marshaller)

    val mapInput: FromInput[Map[String, Any]] = FromInput.defaultInput[Any]
    val mapResult: mapInput.marshaller.Node = Map("id" -> 1, "name" -> "Ada").asInstanceOf[mapInput.marshaller.Node]
    assertEquals(Map("id" -> 1, "name" -> "Ada"), mapInput.fromResult(mapResult))

    val optionInput: FromInput[Option[String @@ FromInput.CoercedScalaResult]] =
      FromInput.optionInput[String @@ FromInput.CoercedScalaResult](using scalarInput)
    assertEquals(Some("configured"), optionInput.fromResult(Some("configured").asInstanceOf[optionInput.marshaller.Node]))
    assertEquals(None, optionInput.fromResult(None.asInstanceOf[optionInput.marshaller.Node]))

    val seqInput: FromInput.SeqFromInput[String @@ FromInput.CoercedScalaResult] =
      FromInput.seqInput[String @@ FromInput.CoercedScalaResult](using scalarInput)
    val seqResult: seqInput.marshaller.Node = Vector("first", Some("second"), None).asInstanceOf[seqInput.marshaller.Node]
    assertEquals(Vector("first", Some("second"), None), seqInput.fromResult(seqResult).toVector)

    val inputObjectInput: FromInput[(String @@ FromInput.CoercedScalaResult) @@ FromInput.InputObjectResult] =
      FromInput.inputObjectResultInput[String @@ FromInput.CoercedScalaResult](using scalarInput)
    assertEquals("object-field", inputObjectInput.fromResult("object-field".asInstanceOf[inputObjectInput.marshaller.Node]))
  }

  @Test
  def marshallingUtilConvertsNestedScalaInputAndProvidesMarshallerConvenienceOps(): Unit = {
    val marshaller: ScalaResultMarshaller = new ScalaResultMarshaller
    val builtNode: marshaller.Node = marshaller.map(
      "id" -> marshaller.fromInt(7),
      "name" -> marshaller.fromString("Ada"),
      "flags" -> marshaller.list(marshaller.fromBoolean(true), marshaller.fromEnumString("ADMIN")),
      "amount" -> marshaller.fromBigInt(BigDecimal("10.50"))
    )
    assertEquals(ListMap("id" -> 7, "name" -> "Ada", "flags" -> Vector(true, "ADMIN"), "amount" -> BigDecimal("10.50")), builtNode)

    val input: Any @@ ScalaInput = ScalaInput.scalaInput(ListMap(
      "profile" -> ListMap("name" -> "Ada", "score" -> BigInt(99)),
      "tags" -> Vector("graphql", "native-image"),
      "enabled" -> true,
      "unset" -> null
    ))
    val converted: marshaller.Node = marshaller.fromInput(input)
    assertEquals(
      ListMap(
        "profile" -> ListMap("name" -> "Ada", "score" -> BigInt(99)),
        "tags" -> Vector("graphql", "native-image"),
        "enabled" -> true,
        "unset" -> null
      ),
      converted
    )

    val convertedWithContextBounds: Any @@ ScalaInput = MarshallingUtil.convert[Any @@ ScalaInput, Any @@ ScalaInput](input)(using
      InputUnmarshaller.scalaInputUnmarshaller[Any],
      scalaMarshalling.ScalaMarshallerForType
    )
    assertEquals(converted, convertedWithContextBounds)
  }

  @Test
  def marshallingUtilUsesEnumNodesForStringEnumInputs(): Unit = {
    given InputUnmarshaller[NamedInputValue] = new InputUnmarshaller[NamedInputValue] {
      override def getRootMapValue(node: NamedInputValue, key: String): Option[NamedInputValue] = None
      override def isMapNode(node: NamedInputValue): Boolean = false
      override def getMapValue(node: NamedInputValue, key: String): Option[NamedInputValue] = None
      override def getMapKeys(node: NamedInputValue): Iterable[String] = Nil
      override def isListNode(node: NamedInputValue): Boolean = false
      override def getListValue(node: NamedInputValue): Seq[NamedInputValue] = Nil
      override def isDefined(node: NamedInputValue): Boolean = true
      override def isScalarNode(node: NamedInputValue): Boolean = true
      override def isEnumNode(node: NamedInputValue): Boolean = node.isEnum
      override def isVariableNode(node: NamedInputValue): Boolean = false
      override def getScalarValue(node: NamedInputValue): Any = node.value
      override def getScalaScalarValue(node: NamedInputValue): Any = node.value
      override def getVariableName(node: NamedInputValue): String = throw new IllegalArgumentException("variables are not supported")
      override def render(node: NamedInputValue): String = node.value.toString
    }

    val marshaller: LabeledScalarMarshaller = new LabeledScalarMarshaller
    given ResultMarshallerForType[String] = SimpleResultMarshallerForType[String](marshaller)

    assertEquals("enum:Conversion:ACTIVE", MarshallingUtil.convert[NamedInputValue, String](NamedInputValue("ACTIVE", isEnum = true)))
    assertEquals("scalar:Conversion:15", MarshallingUtil.convert[NamedInputValue, String](NamedInputValue(15, isEnum = true)))
  }

  @Test
  def marshallingUtilRejectsVariableNodesDuringConversion(): Unit = {
    given InputUnmarshaller[VariableInput] = new InputUnmarshaller[VariableInput] {
      override def getRootMapValue(node: VariableInput, key: String): Option[VariableInput] = None
      override def isMapNode(node: VariableInput): Boolean = false
      override def getMapValue(node: VariableInput, key: String): Option[VariableInput] = None
      override def getMapKeys(node: VariableInput): Iterable[String] = Nil
      override def isListNode(node: VariableInput): Boolean = false
      override def getListValue(node: VariableInput): Seq[VariableInput] = Nil
      override def isDefined(node: VariableInput): Boolean = true
      override def isScalarNode(node: VariableInput): Boolean = false
      override def isEnumNode(node: VariableInput): Boolean = false
      override def isVariableNode(node: VariableInput): Boolean = true
      override def getScalarValue(node: VariableInput): Any = node.name
      override def getScalaScalarValue(node: VariableInput): Any = node.name
      override def getVariableName(node: VariableInput): String = node.name
      override def render(node: VariableInput): String = s"$$${node.name}"
    }
    given ResultMarshallerForType[Any] = SimpleResultMarshallerForType[Any](new ScalaResultMarshaller)

    val exception: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => MarshallingUtil.convert[VariableInput, Any](VariableInput("viewerId"))
    )
    assertEquals(
      "Variable 'viewerId' found in the input, but variables are not supported in conversion!",
      exception.getMessage
    )
  }

  @Test
  def symmetricMarshallerCapabilitiesParsingErrorsAndParsersUsePublicContracts(): Unit = {
    val defaultSymmetric: SymmetricMarshaller[Any] = SymmetricMarshaller.defaultInput
    assertSame(scalaMarshalling.scalaResultMarshaller, defaultSymmetric.marshaller)
    assertTrue(defaultSymmetric.inputUnmarshaller.isMapNode(Map("key" -> "value")))

    val explicitSymmetric: SymmetricMarshaller[Any @@ ScalaInput] = SymmetricMarshaller.symmetric[Any @@ ScalaInput](using
      scalaMarshalling.ScalaMarshallerForType,
      InputUnmarshaller.scalaInputUnmarshaller[Any]
    )
    assertSame(scalaMarshalling.scalaResultMarshaller, explicitSymmetric.marshaller)
    assertTrue(explicitSymmetric.inputUnmarshaller.isListNode(ScalaInput.scalaInput(Vector(1, 2))))

    assertTrue(DateSupport.isInstanceOf[StandardMarshallerCapability])
    assertTrue(CalendarSupport.isInstanceOf[StandardMarshallerCapability])
    assertTrue(BlobSupport.isInstanceOf[StandardMarshallerCapability])
    assertEquals("DateSupport", DateSupport.toString)
    assertEquals("CalendarSupport", CalendarSupport.productPrefix)
    assertNotEquals(DateSupport, BlobSupport)

    val parsingError: InputParsingError = InputParsingError(Vector("first error", "second error"))
    assertEquals(Vector("first error", "second error"), parsingError.errors)
    assertEquals("first error\nsecond error", parsingError.getMessage)
    assertEquals(parsingError, parsingError.copy())

    val parser: InputParser[Int] = (str: String) => Try(str.toInt).filter(_ >= 0)
    assertEquals(123, parser.parse("123").get)
    assertTrue(parser.parse("not-an-int").isFailure)
    assertTrue(parser.parse("-1").isFailure)
  }

  private def assertScalarToInput[T](toInput: ToInput[T, T @@ ScalaInput], value: T): Unit = {
    val (rawValue, unmarshaller) = toInput.toInput(value)
    assertTrue(unmarshaller.isScalarNode(rawValue))
    assertEquals(value, unmarshaller.getScalarValue(rawValue))
    assertEquals(value, unmarshaller.getScalaScalarValue(rawValue))
  }

  private final class LabeledScalarMarshaller extends ResultMarshaller {
    override type Node = String
    override type MapBuilder = Vector[(String, Node)]

    override def emptyMapNode(keys: Seq[String]): MapBuilder = Vector.empty
    override def addMapNodeElem(builder: MapBuilder, key: String, value: Node, optional: Boolean): MapBuilder = builder :+ (key -> value)
    override def mapNode(builder: MapBuilder): Node = builder.map { case (key, value) => s"$key=$value" }.mkString("map(", ",", ")")
    override def mapNode(keyValues: Seq[(String, Node)]): Node = keyValues.map { case (key, value) => s"$key=$value" }.mkString("map(", ",", ")")
    override def arrayNode(values: Vector[Node]): Node = values.mkString("list(", ",", ")")
    override def optionalArrayNodeValue(value: Option[Node]): Node = value.getOrElse(nullNode)
    override def scalarNode(value: Any, typeName: String, info: Set[ScalarValueInfo]): Node = s"scalar:$typeName:$value"
    override def enumNode(value: String, typeName: String): Node = s"enum:$typeName:$value"
    override def nullNode: Node = "null"
    override def renderCompact(node: Node): String = node
    override def renderPretty(node: Node): String = node
  }

  private final case class NamedInputValue(value: Any, isEnum: Boolean)
  private final case class VariableInput(name: String)
}
