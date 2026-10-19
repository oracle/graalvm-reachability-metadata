/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.ByteString
import com.google.protobuf.`type`.Field
import com.google.protobuf.`type`.Syntax
import com.google.protobuf.api.Api
import com.google.protobuf.api.Method
import com.google.protobuf.api.Mixin
import com.google.protobuf.source_context.SourceContext
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers.BytesValue
import com.google.protobuf.wrappers.DoubleValue
import com.google.protobuf.wrappers.Int64Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.Encoding
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion
import scalapb.StructUtils
import scalapb.StructUtils.StructParsingError
import scalapb.descriptors.ReadsException

import java.nio.charset.StandardCharsets
import scala.collection.mutable

/**
 * Exercises conversion between generated messages and `google.protobuf.Struct` documents with
 * `StructUtils.toStruct` and `StructUtils.fromStruct`, including its `StructParsingError` results.
 *
 * A service directory keeps every registered API definition as a schemaless `Struct` document in
 * a document store, so operators can browse and hand-edit entries in an admin console. The
 * directory converts typed messages to documents on write, decodes documents back into typed
 * messages on read, fills hand-edited documents in from a template of defaults, and reports a
 * readable error when a hand-edited document no longer fits the schema.
 */
class ServiceDirectoryStructDocumentTest {

  @Test
  def registeredApisAreStoredAsStructDocumentsAndReadBackUnchanged(): Unit = {
    val store: DocumentStore = new DocumentStore
    val directory: ServiceDirectory = new ServiceDirectory(store)
    val billing: Api = Api(
      name = "acme.billing.v1.Invoices",
      methods = Seq(
        Method(
          name = "CreateInvoice",
          requestTypeUrl = "type.googleapis.com/acme.billing.v1.CreateInvoiceRequest",
          responseTypeUrl = "type.googleapis.com/acme.billing.v1.Invoice"
        ),
        Method(
          name = "WatchInvoices",
          requestTypeUrl = "type.googleapis.com/acme.billing.v1.WatchRequest",
          responseTypeUrl = "type.googleapis.com/acme.billing.v1.Invoice",
          responseStreaming = true
        )
      ),
      version = "1.4.0",
      sourceContext = Some(SourceContext(fileName = "acme/billing/v1/invoices.proto")),
      mixins = Seq(Mixin(name = "google.longrunning.Operations", root = "v1")),
      syntax = Syntax.SYNTAX_PROTO3
    )

    directory.register(billing)

    val document: Struct = store.read("acme.billing.v1.Invoices")
    assertEquals(Some(Value.Kind.StringValue("1.4.0")), document.fields.get("version").map(_.kind))
    assertEquals(
      Some(Value.Kind.StringValue("SYNTAX_PROTO3")),
      document.fields.get("syntax").map(_.kind)
    )
    val context: Struct = document.fields("source_context").getStructValue
    assertEquals("acme/billing/v1/invoices.proto", context.fields("file_name").getStringValue)
    val methods: Seq[Value] = document.fields("methods").getListValue.values
    assertEquals(
      Seq("CreateInvoice", "WatchInvoices"),
      methods.map(_.getStructValue.fields("name").getStringValue)
    )
    assertEquals(
      Seq(false, true),
      methods.map(_.getStructValue.fields("response_streaming").getBoolValue)
    )
    assertFalse(document.fields.contains("options"), "empty repeated fields are left out")

    assertEquals(Right(billing), directory.lookup("acme.billing.v1.Invoices"))
  }

  @Test
  def unsetFieldsAreOmittedAndSparseDocumentsNeedTheDefaultsTemplate(): Unit = {
    val minimal: Api = Api(name = "acme.health.v1.Health")
    val document: Struct = StructUtils.toStruct(minimal)

    assertEquals(Some("acme.health.v1.Health"), document.fields.get("name").map(_.getStringValue))
    assertFalse(document.fields.contains("source_context"), "an unset message field is omitted")
    assertFalse(document.fields.contains("methods"))
    assertFalse(document.fields.contains("mixins"))
    assertEquals("", document.fields("version").getStringValue, "scalar defaults are written")
    assertEquals("SYNTAX_PROTO2", document.fields("syntax").getStringValue)

    val sparse: Struct = Struct(Map("name" -> stringValue("acme.health.v1.Health")))
    assertThrows(classOf[ReadsException], () => StructUtils.fromStruct[Api](sparse))

    val store: DocumentStore = new DocumentStore
    val directory: ServiceDirectory = new ServiceDirectory(store)
    store.write("acme.health.v1.Health", sparse)
    val decoded: Either[String, Api] = directory.lookupEdited("acme.health.v1.Health")
    assertEquals(Right(minimal), decoded)
    assertEquals(None, decoded.toOption.get.sourceContext)
    assertEquals(Syntax.SYNTAX_PROTO2, decoded.toOption.get.syntax)
  }

  @Test
  def longsAndBytesSurviveTheDocumentModelAsStrings(): Unit = {
    val releasedAt: Timestamp = Timestamp(seconds = 1767225600L, nanos = 125000000)
    val releaseDocument: Struct = StructUtils.toStruct(releasedAt)
    assertEquals(Value.Kind.StringValue("1767225600"), releaseDocument.fields("seconds").kind)
    assertEquals(Value.Kind.NumberValue(125000000.0), releaseDocument.fields("nanos").kind)
    assertEquals(Right(releasedAt), StructUtils.fromStruct[Timestamp](releaseDocument))

    val counter: Int64Value = Int64Value(9007199254740993L)
    val counterDocument: Struct = StructUtils.toStruct(counter)
    assertEquals(
      Value.Kind.StringValue("9007199254740993"),
      counterDocument.fields("value").kind,
      "int64 values are written as strings so JSON numbers do not lose precision"
    )
    assertEquals(Right(counter), StructUtils.fromStruct[Int64Value](counterDocument))

    val fingerprint: Array[Byte] = "sha256:4f2a".getBytes(StandardCharsets.US_ASCII)
    val signature: BytesValue = BytesValue(ByteString.copyFrom(fingerprint))
    val signatureDocument: Struct = StructUtils.toStruct(signature)
    assertEquals(
      Value.Kind.StringValue(Encoding.toBase64(fingerprint)),
      signatureDocument.fields("value").kind
    )
    assertEquals(Right(signature), StructUtils.fromStruct[BytesValue](signatureDocument))

    val ratio: DoubleValue = DoubleValue(0.875)
    assertEquals(Value.Kind.NumberValue(0.875), StructUtils.toStruct(ratio).fields("value").kind)
    assertEquals(Right(ratio), StructUtils.fromStruct[DoubleValue](StructUtils.toStruct(ratio)))
  }

  @Test
  def handEditedDocumentsDecodeIntoTypedSchemaFields(): Unit = {
    val edited: Struct = Struct(
      Map(
        "name" -> stringValue("tags"),
        "kind" -> stringValue("TYPE_STRING"),
        "cardinality" -> stringValue("CARDINALITY_REPEATED"),
        "number" -> Value(Value.Kind.NumberValue(7.0)),
        "json_name" -> stringValue("tags"),
        "packed" -> Value(Value.Kind.BoolValue(false)),
        "options" -> Value(
          Value.Kind.ListValue(
            ListValue(
              Seq(Value(Value.Kind.StructValue(Struct(Map("name" -> stringValue("deprecated"))))))
            )
          )
        )
      )
    )

    val field: Field = StructUtils.fromStruct[Field](withDefaults[Field](edited)) match {
      case Right(decoded) => decoded
      case Left(StructParsingError(error)) => throw new AssertionError(error)
    }
    assertEquals("tags", field.name)
    assertEquals(Field.Kind.TYPE_STRING, field.kind)
    assertEquals(Field.Cardinality.CARDINALITY_REPEATED, field.cardinality)
    assertEquals(7, field.number)
    assertEquals(Seq("deprecated"), field.options.map(_.name))
    assertEquals("", field.typeUrl, "fields missing from the document take their default")
    assertEquals(0, field.oneofIndex)
  }

  @Test
  def documentsThatNoLongerFitTheSchemaAreRejectedWithReadableErrors(): Unit = {
    val store: DocumentStore = new DocumentStore
    val directory: ServiceDirectory = new ServiceDirectory(store)

    store.write(
      "acme.search.v1.Search",
      Struct(
        Map(
          "name" -> stringValue("acme.search.v1.Search"),
          "syntax" -> stringValue("SYNTAX_PROTO4")
        )
      )
    )
    assertEquals(
      Left(
        "acme.search.v1.Search: Field \"google.protobuf.Api.syntax\" is of type enum " +
          "\"google.protobuf.Syntax\" but received invalid enum value \"SYNTAX_PROTO4\""
      ),
      directory.lookup("acme.search.v1.Search")
    )

    val badSeconds: Either[StructParsingError, Timestamp] = StructUtils.fromStruct[Timestamp](
      Struct(Map("seconds" -> stringValue("yesterday")))
    )
    assertEquals(
      Left(
        StructParsingError(
          "Field \"google.protobuf.Timestamp.seconds\" is of type long but received invalid " +
            "long value \"yesterday\""
        )
      ),
      badSeconds
    )

    val fractionalNumber: Either[StructParsingError, Field] =
      StructUtils.fromStruct[Field](
        withDefaults[Field](Struct(Map("number" -> Value(Value.Kind.NumberValue(2.5)))))
      )
    assertTrue(fractionalNumber.isLeft, "a non-integral number cannot fill an int32 field")
    assertTrue(
      fractionalNumber.left.toOption.get.error.startsWith("Field \"google.protobuf.Field.number\"")
    )

    val nestedMismatch: Either[StructParsingError, Api] = StructUtils.fromStruct[Api](
      Struct(
        Map(
          "methods" -> Value(
            Value.Kind.ListValue(
              ListValue(
                Seq(
                  Value(
                    Value.Kind.StructValue(
                      Struct(Map("request_streaming" -> stringValue("yes")))
                    )
                  )
                )
              )
            )
          )
        )
      )
    )
    assertTrue(
      nestedMismatch.left.toOption.get.error
        .startsWith("Field \"google.protobuf.Method.request_streaming\""),
      "errors inside nested documents name the nested field"
    )

    val repaired: Api = Api(name = "acme.search.v1.Search")
    assertEquals(Right(repaired), directory.repair("acme.search.v1.Search"))
    assertEquals(Right(repaired), directory.lookup("acme.search.v1.Search"))
  }

  private def stringValue(text: String): Value = Value(Value.Kind.StringValue(text))

  /** Completes a hand-edited document with the fields a default message would carry. */
  private def withDefaults[T <: GeneratedMessage](edited: Struct)(implicit
      companion: GeneratedMessageCompanion[T]
  ): Struct = Struct(StructUtils.toStruct(companion.defaultInstance).fields ++ edited.fields)

  /** Schemaless storage that persists documents as serialized `Struct` bytes. */
  private final class DocumentStore {
    private val documents: mutable.Map[String, Array[Byte]] = mutable.LinkedHashMap.empty

    def write(key: String, document: Struct): Unit = documents.update(key, document.toByteArray)

    def read(key: String): Struct = Struct.parseFrom(documents(key))
  }

  /** Typed facade that the directory's callers use to register and look up APIs. */
  private final class ServiceDirectory(store: DocumentStore) {
    def register(api: Api): Unit = store.write(api.name, StructUtils.toStruct(api))

    def lookup(name: String): Either[String, Api] = decode[Api](name, store.read(name))

    /** Reads an entry an operator edited by hand, which may leave out default fields. */
    def lookupEdited(name: String): Either[String, Api] =
      decode[Api](name, withDefaults[Api](store.read(name)))

    /** Drops the document fields that no longer decode and rewrites the entry. */
    def repair(name: String): Either[String, Api] = {
      val document: Struct = store.read(name)
      val kept: Map[String, Value] = document.fields.filter { case (key, value) =>
        StructUtils.fromStruct[Api](withDefaults[Api](Struct(Map(key -> value)))).isRight
      }
      decode[Api](name, withDefaults[Api](Struct(kept))).map { api =>
        register(api)
        api
      }
    }

    private def decode[T <: GeneratedMessage](key: String, document: Struct)(implicit
        companion: GeneratedMessageCompanion[T]
    ): Either[String, T] =
      StructUtils.fromStruct[T](document).left.map(error => s"$key: ${error.error}")
  }
}
