/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.source_context.SourceContext
import com.google.protobuf.`type`.Field
import com.google.protobuf.`type`.Syntax
import com.google.protobuf.`type`.Type
import com.google.protobuf.`type`.TypeProto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion
import scalapb.descriptors.Descriptor
import scalapb.descriptors.FieldDescriptor
import scalapb.descriptors.PBoolean
import scalapb.descriptors.PEmpty
import scalapb.descriptors.PEnum
import scalapb.descriptors.PInt
import scalapb.descriptors.PLong
import scalapb.descriptors.PMessage
import scalapb.descriptors.PRepeated
import scalapb.descriptors.PString
import scalapb.descriptors.PValue
import scalapb.descriptors.ScalaType

/**
 * Exercises descriptor-based message reflection: `scalaDescriptor`, `getField`, `toPMessage`
 * and `messageReads` read and build generated messages field by field without knowing their
 * Scala classes.
 *
 * A schema registry stores published record schemas as `google.protobuf.Type` messages. Its
 * admin console never names the generated classes: it walks descriptors to render any stored
 * message as flat `path = value` lines, to diff two schema revisions for a change log, and to
 * build messages from the untyped key/value forms operators submit.
 */
class SchemaRegistryDescriptorReflectionTest {
  private val customerV1: Type = Type(
    name = "shop.Customer",
    fields = Seq(
      Field(kind = Field.Kind.TYPE_STRING, cardinality = Field.Cardinality.CARDINALITY_OPTIONAL,
        number = 1, name = "id", jsonName = "id"),
      Field(kind = Field.Kind.TYPE_STRING, cardinality = Field.Cardinality.CARDINALITY_OPTIONAL,
        number = 2, name = "email", jsonName = "email")
    ),
    sourceContext = Some(SourceContext(fileName = "shop/customer.proto")),
    syntax = Syntax.SYNTAX_PROTO3
  )

  @Test
  def consoleDescribesStoredSchemasFromTheirDescriptors(): Unit = {
    val schema: Descriptor = Type.scalaDescriptor
    assertEquals("google.protobuf.Type", schema.fullName)
    assertEquals("google.protobuf", schema.file.packageName)
    assertTrue(TypeProto.scalaDescriptor.messages.exists(_ eq schema))

    val columns: Seq[String] = RegistryConsole.describeColumns(schema)
    assertEquals(
      Seq(
        "1 name: string",
        "2 fields: repeated google.protobuf.Field",
        "3 oneofs: repeated string",
        "4 options: repeated google.protobuf.Option",
        "5 source_context: google.protobuf.SourceContext",
        "6 syntax: enum google.protobuf.Syntax"
      ),
      columns
    )

    val kindField: FieldDescriptor = Field.scalaDescriptor.findFieldByName("kind").get
    val kinds: Seq[String] = kindField.scalaType match {
      case ScalaType.Enum(kindEnum) => kindEnum.values.map(_.name).take(4)
      case other => throw new AssertionError(s"kind is not an enum: $other")
    }
    assertEquals(Seq("TYPE_UNKNOWN", "TYPE_DOUBLE", "TYPE_FLOAT", "TYPE_INT64"), kinds)
    assertEquals(Some(kindField), Field.scalaDescriptor.findFieldByNumber(Field.KIND_FIELD_NUMBER))
  }

  @Test
  def consoleRendersAnyStoredMessageAsFlatPathValueLines(): Unit = {
    val lines: Seq[String] = RegistryConsole.render(customerV1)
    assertEquals(
      Seq(
        "name = shop.Customer",
        "fields[0].kind = TYPE_STRING",
        "fields[0].cardinality = CARDINALITY_OPTIONAL",
        "fields[0].number = 1",
        "fields[0].name = id",
        "fields[0].json_name = id",
        "fields[1].kind = TYPE_STRING",
        "fields[1].cardinality = CARDINALITY_OPTIONAL",
        "fields[1].number = 2",
        "fields[1].name = email",
        "fields[1].json_name = email",
        "source_context.file_name = shop/customer.proto",
        "syntax = SYNTAX_PROTO3"
      ),
      lines
    )

    val nameField: FieldDescriptor = Type.scalaDescriptor.findFieldByName("name").get
    assertEquals(PString("shop.Customer"), customerV1.getField(nameField))
    assertEquals("shop.Customer", customerV1.getFieldByNumber(Type.NAME_FIELD_NUMBER))
    val contextField: FieldDescriptor = Type.scalaDescriptor.findFieldByName("source_context").get
    assertEquals(PEmpty, customerV1.clearSourceContext.getField(contextField))
  }

  @Test
  def changeLogListsEveryPathThatDiffersBetweenSchemaRevisions(): Unit = {
    val customerV2: Type = customerV1
      .withFields(customerV1.fields.updated(1, customerV1.fields(1).withJsonName("emailAddress")) :+
        Field(kind = Field.Kind.TYPE_BOOL, cardinality = Field.Cardinality.CARDINALITY_OPTIONAL,
          number = 3, name = "marketing_opt_in", jsonName = "marketingOptIn", packed = true))
      .withSyntax(Syntax.SYNTAX_PROTO2)

    val changes: Seq[String] = RegistryConsole.diff(customerV1, customerV2)
    assertEquals(
      Seq(
        "~ fields[1].json_name: email -> emailAddress",
        "+ fields[2].kind: TYPE_BOOL",
        "+ fields[2].cardinality: CARDINALITY_OPTIONAL",
        "+ fields[2].number: 3",
        "+ fields[2].name: marketing_opt_in",
        "+ fields[2].packed: true",
        "+ fields[2].json_name: marketingOptIn",
        // SYNTAX_PROTO2 is enum number 0, the proto3 default, so the field reads as cleared.
        "- syntax: SYNTAX_PROTO3"
      ),
      changes
    )
    assertTrue(RegistryConsole.diff(customerV2, customerV2).isEmpty)
  }

  @Test
  def operatorFormsBuildTypedMessagesThroughMessageReads(): Unit = {
    val submitted: Map[String, String] = Map(
      "name" -> "loyalty_tier",
      "number" -> "4",
      "kind" -> "TYPE_ENUM",
      "cardinality" -> "CARDINALITY_OPTIONAL",
      "type_url" -> "type.googleapis.com/shop.LoyaltyTier",
      "packed" -> "false"
    )
    val built: Field = RegistryConsole.fromForm(Field, submitted)
    assertEquals(
      Field(kind = Field.Kind.TYPE_ENUM, cardinality = Field.Cardinality.CARDINALITY_OPTIONAL,
        number = 4, name = "loyalty_tier", typeUrl = "type.googleapis.com/shop.LoyaltyTier"),
      built
    )

    val roundTripped: Type = Type.messageReads.read(customerV1.toPMessage)
    assertEquals(customerV1, roundTripped)
    assertEquals(customerV1, Type.parseFrom(roundTripped.toByteArray))

    val unknownColumn: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => RegistryConsole.fromForm(Field, Map("nickname" -> "x"))
    )
    assertEquals("google.protobuf.Field has no field nickname", unknownColumn.getMessage)
    assertFalse(Field.scalaDescriptor.findFieldByName("nickname").isDefined)
  }

  /** The registry console: renders, diffs and builds messages through descriptors only. */
  object RegistryConsole {
    /** One line per declared field: number, name, and its type as the descriptor reports it. */
    def describeColumns(descriptor: Descriptor): Seq[String] =
      descriptor.fields.map { field =>
        val typeName: String = field.scalaType match {
          case ScalaType.Message(nested) => nested.fullName
          case ScalaType.Enum(enumType) => s"enum ${enumType.fullName}"
          case ScalaType.String => "string"
          case ScalaType.Int => "int32"
          case ScalaType.Boolean => "bool"
          case other => other.toString
        }
        val label: String = if (field.isRepeated) "repeated " else ""
        s"${field.number} ${field.name}: $label$typeName"
      }

    /** Flattens any message into `(path, value)` pairs, skipping proto3 defaults and unset fields. */
    def flatten(message: GeneratedMessage): Seq[(String, String)] =
      flattenFields("", message.toPMessage)

    /** Renders any message as the `path = value` lines the console shows. */
    def render(message: GeneratedMessage): Seq[String] =
      flatten(message).map { case (path, value) => s"$path = $value" }

    /** Compares two revisions path by path, in the order the newer revision declares them. */
    def diff(before: GeneratedMessage, after: GeneratedMessage): Seq[String] = {
      val old: Map[String, String] = flatten(before).toMap
      val updated: Seq[(String, String)] = flatten(after)
      val newPaths: Set[String] = updated.map(_._1).toSet
      val changedOrAdded: Seq[String] = updated.flatMap { case (path, value) =>
        old.get(path) match {
          case None => Some(s"+ $path: $value")
          case Some(previous) if previous != value => Some(s"~ $path: $previous -> $value")
          case Some(_) => None
        }
      }
      val removed: Seq[String] = flatten(before).collect {
        case (path, value) if !newPaths.contains(path) => s"- $path: $value"
      }
      changedOrAdded ++ removed
    }

    /** Builds a message of any generated type from the string form fields an operator submits. */
    def fromForm[A <: GeneratedMessage](
        companion: GeneratedMessageCompanion[A],
        form: Map[String, String]
    ): A = {
      val descriptor: Descriptor = companion.scalaDescriptor
      val values: Map[FieldDescriptor, PValue] = form.map { case (name, text) =>
        val field: FieldDescriptor = descriptor.findFieldByName(name).getOrElse(
          throw new IllegalArgumentException(s"${descriptor.fullName} has no field $name"))
        val value: PValue = field.scalaType match {
          case ScalaType.String => PString(text)
          case ScalaType.Int => PInt(text.toInt)
          case ScalaType.Long => PLong(text.toLong)
          case ScalaType.Boolean => PBoolean(text.toBoolean)
          case ScalaType.Enum(enumType) => PEnum(enumType.values.find(_.name == text).getOrElse(
            throw new IllegalArgumentException(s"${enumType.fullName} has no value $text")))
          case other => throw new IllegalArgumentException(s"$name of type $other is not editable")
        }
        field -> value
      }
      companion.messageReads.read(PMessage(values))
    }

    private def flattenFields(prefix: String, message: PMessage): Seq[(String, String)] =
      message.value.toSeq.sortBy(_._1.index).flatMap { case (field, value) =>
        flattenValue(prefix + field.name, value)
      }

    private def flattenValue(path: String, value: PValue): Seq[(String, String)] = value match {
      case PEmpty => Nil
      case PString("") | PInt(0) | PLong(0L) | PBoolean(false) => Nil
      case PEnum(enumValue) if enumValue.number == 0 => Nil
      case PString(text) => Seq(path -> text)
      case PInt(number) => Seq(path -> number.toString)
      case PLong(number) => Seq(path -> number.toString)
      case PBoolean(flag) => Seq(path -> flag.toString)
      case PEnum(enumValue) => Seq(path -> enumValue.name)
      case nested: PMessage => flattenFields(path + ".", nested)
      case PRepeated(elements) =>
        elements.zipWithIndex.flatMap { case (element, i) => flattenValue(s"$path[$i]", element) }
      case other => Seq(path -> other.toString)
    }
  }
}
