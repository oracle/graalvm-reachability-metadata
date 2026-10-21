/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.compiler.plugin.CodeGeneratorRequest
import com.google.protobuf.compiler.plugin.CodeGeneratorResponse
import com.google.protobuf.descriptor.DescriptorProto
import com.google.protobuf.descriptor.EnumDescriptorProto
import com.google.protobuf.descriptor.EnumValueDescriptorProto
import com.google.protobuf.descriptor.FieldDescriptorProto
import com.google.protobuf.descriptor.FileDescriptorProto
import com.google.protobuf.descriptor.FileOptions
import com.google.protobuf.descriptor.{EnumOptions => ProtoEnumOptions}
import com.google.protobuf.descriptor.{FieldOptions => ProtoFieldOptions}
import com.google.protobuf.descriptor.{MessageOptions => ProtoMessageOptions}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.descriptors.Descriptor
import scalapb.descriptors.EnumDescriptor
import scalapb.descriptors.FieldDescriptor
import scalapb.descriptors.FileDescriptor
import scalapb.descriptors.ScalaType
import scalapb.options.EnumOptions
import scalapb.options.FieldOptions
import scalapb.options.MessageOptions
import scalapb.options.ScalaPbOptions
import scalapb.options.ScalapbProto

/**
 * Exercises protobuf extensions through `GeneratedExtension` and `ExtendableMessage`: ScalaPB's
 * custom options are attached to descriptor options with `withExtension`, read back with
 * `extension`, updated as lenses, and recovered from descriptors built by
 * `FileDescriptor.buildFrom`.
 *
 * A billing team ships its own protoc plugin that emits Scala model sources. protoc hands the
 * plugin a serialized `CodeGeneratorRequest` holding every `.proto` file it parsed; the plugin
 * builds descriptors from them, honours the `(scalapb.options)`, `(scalapb.message)`,
 * `(scalapb.field)` and `(scalapb.enum_options)` annotations the schema authors wrote, and answers
 * with a `CodeGeneratorResponse` listing the generated files.
 */
class CodegenPluginOptionsExtensionTest {
  private val moneyProto: FileDescriptorProto = FileDescriptorProto(
    name = Some("billing/money.proto"),
    `package` = Some("billing"),
    messageType = Seq(
      DescriptorProto(
        name = Some("Money"),
        field = Seq(
          FieldDescriptorProto(name = Some("currency"), number = Some(1),
            label = Some(FieldDescriptorProto.Label.LABEL_OPTIONAL),
            `type` = Some(FieldDescriptorProto.Type.TYPE_STRING), jsonName = Some("currency")),
          FieldDescriptorProto(name = Some("units"), number = Some(2),
            label = Some(FieldDescriptorProto.Label.LABEL_OPTIONAL),
            `type` = Some(FieldDescriptorProto.Type.TYPE_INT64), jsonName = Some("units"))
        )
      )
    ),
    syntax = Some("proto3")
  )

  private val invoiceProto: FileDescriptorProto = FileDescriptorProto(
    name = Some("billing/invoice.proto"),
    `package` = Some("billing"),
    dependency = Seq("billing/money.proto"),
    messageType = Seq(
      DescriptorProto(
        name = Some("Invoice"),
        field = Seq(
          FieldDescriptorProto(name = Some("id"), number = Some(1),
            label = Some(FieldDescriptorProto.Label.LABEL_OPTIONAL),
            `type` = Some(FieldDescriptorProto.Type.TYPE_STRING), jsonName = Some("id")),
          FieldDescriptorProto(name = Some("total_cents"), number = Some(2),
            label = Some(FieldDescriptorProto.Label.LABEL_OPTIONAL),
            `type` = Some(FieldDescriptorProto.Type.TYPE_INT64), jsonName = Some("totalCents"),
            options = Some(ProtoFieldOptions().withExtension(ScalapbProto.field)(
              Some(FieldOptions().withType("com.acme.money.Cents").withScalaName("total"))))),
          FieldDescriptorProto(name = Some("tax"), number = Some(3),
            label = Some(FieldDescriptorProto.Label.LABEL_OPTIONAL),
            `type` = Some(FieldDescriptorProto.Type.TYPE_MESSAGE),
            typeName = Some(".billing.Money"), jsonName = Some("tax")),
          FieldDescriptorProto(name = Some("status"), number = Some(4),
            label = Some(FieldDescriptorProto.Label.LABEL_OPTIONAL),
            `type` = Some(FieldDescriptorProto.Type.TYPE_ENUM),
            typeName = Some(".billing.InvoiceStatus"), jsonName = Some("status"))
        ),
        options = Some(ProtoMessageOptions().withExtension(ScalapbProto.message)(
          Some(MessageOptions().withExtends(Seq("com.acme.billing.Auditable")))))
      )
    ),
    enumType = Seq(
      EnumDescriptorProto(
        name = Some("InvoiceStatus"),
        value = Seq(
          EnumValueDescriptorProto(name = Some("DRAFT"), number = Some(0)),
          EnumValueDescriptorProto(name = Some("ISSUED"), number = Some(1)),
          EnumValueDescriptorProto(name = Some("PAID"), number = Some(2))
        ),
        options = Some(ProtoEnumOptions().withExtension(ScalapbProto.enumOptions)(
          Some(EnumOptions().withExtends(Seq("com.acme.billing.Labelled")))))
      )
    ),
    options = Some(FileOptions(javaPackage = Some("com.acme.billing.java"))
      .withExtension(ScalapbProto.options)(
        Some(ScalaPbOptions().withPackageName("com.acme.billing.model").withFlatPackage(true)))),
    syntax = Some("proto3")
  )

  /** The plugin process: bytes from protoc in, a response for protoc out. */
  private object ModelPlugin {
    def run(requestBytes: Array[Byte]): CodeGeneratorResponse = {
      val request: CodeGeneratorRequest = CodeGeneratorRequest.parseFrom(requestBytes)
      request.parameter.filterNot(_ == "models") match {
        case Some(unsupported) =>
          CodeGeneratorResponse(error = Some(s"unsupported parameter: $unsupported"))
        case None =>
          val built: Map[String, FileDescriptor] = request.protoFile.foldLeft(
            Map.empty[String, FileDescriptor]) { (done, proto) =>
            done + (proto.getName -> FileDescriptor.buildFrom(proto, proto.dependency.map(done)))
          }
          CodeGeneratorResponse(
            supportedFeatures =
              Some(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.value.toLong),
            file = request.fileToGenerate.map(name => render(built(name)))
          )
      }
    }

    def scalaPackage(file: FileDescriptor): String =
      file.getOptions.extension(ScalapbProto.options).flatMap(_.packageName)
        .getOrElse(file.packageName)

    private def render(file: FileDescriptor): CodeGeneratorResponse.File = {
      val pkg: String = scalaPackage(file)
      val baseName: String = file.asProto.getName.split('/').last.stripSuffix(".proto")
      val lines: Seq[String] = Seq(s"package $pkg") ++
        file.messages.map(renderMessage) ++ file.enums.map(renderEnum)
      CodeGeneratorResponse.File(
        name = Some(pkg.replace('.', '/') + "/" + baseName + ".scala"),
        content = Some(lines.mkString("", "\n", "\n"))
      )
    }

    private def renderMessage(message: Descriptor): String = {
      val parents: Seq[String] = message.getOptions.extension(ScalapbProto.message)
        .map(_.`extends`).getOrElse(Nil)
      val params: String = message.fields.map(renderField).mkString(", ")
      s"final case class ${message.name}($params)" + extendsClause(parents)
    }

    private def renderField(field: FieldDescriptor): String = {
      val custom: Option[FieldOptions] = field.getOptions.extension(ScalapbProto.field)
      val name: String = custom.flatMap(_.scalaName).getOrElse(field.name)
      val baseType: String = field.scalaType match {
        case ScalaType.String => "String"
        case ScalaType.Long => "Long"
        case ScalaType.Message(descriptor) => s"Option[${descriptor.name}]"
        case ScalaType.Enum(descriptor) => descriptor.name
        case other => throw new IllegalStateException(s"no mapping for $other")
      }
      s"$name: ${custom.flatMap(_.`type`).getOrElse(baseType)}"
    }

    private def renderEnum(enumType: EnumDescriptor): String = {
      val parents: Seq[String] = enumType.getOptions.extension(ScalapbProto.enumOptions)
        .map(_.`extends`).getOrElse(Nil)
      s"sealed trait ${enumType.name}" + extendsClause(parents) +
        enumType.values.map(_.name).mkString(" { ", ", ", " }")
    }

    private def extendsClause(parents: Seq[String]): String =
      if (parents.isEmpty) "" else parents.mkString(" extends ", " with ", "")
  }

  @Test
  def pluginGeneratesModelsHonouringScalapbAnnotations(): Unit = {
    val request: CodeGeneratorRequest = CodeGeneratorRequest(
      fileToGenerate = Seq("billing/invoice.proto"),
      parameter = Some("models"),
      protoFile = Seq(moneyProto, invoiceProto)
    )

    val response: CodeGeneratorResponse =
      CodeGeneratorResponse.parseFrom(ModelPlugin.run(request.toByteArray).toByteArray)

    assertEquals(None, response.error)
    assertEquals(Some(1L), response.supportedFeatures)
    assertEquals(Seq("com/acme/billing/model/invoice.scala"), response.file.map(_.getName))
    assertEquals(
      Seq(
        "package com.acme.billing.model",
        "final case class Invoice(id: String, total: com.acme.money.Cents, tax: Option[Money], " +
          "status: InvoiceStatus) extends com.acme.billing.Auditable",
        "sealed trait InvoiceStatus extends com.acme.billing.Labelled { DRAFT, ISSUED, PAID }"
      ),
      response.file.head.getContent.split('\n').toSeq
    )
  }

  @Test
  def unannotatedFilesFallBackToTheirProtoPackage(): Unit = {
    val request: CodeGeneratorRequest = CodeGeneratorRequest(
      fileToGenerate = Seq("billing/money.proto"),
      protoFile = Seq(moneyProto)
    )

    val response: CodeGeneratorResponse = ModelPlugin.run(request.toByteArray)

    val money: FileDescriptor = FileDescriptor.buildFrom(moneyProto, Nil)
    assertEquals(None, money.getOptions.extension(ScalapbProto.options))
    assertEquals(None, money.messages.head.getOptions.extension(ScalapbProto.message))
    assertEquals("billing", ModelPlugin.scalaPackage(money))
    assertEquals(Seq("billing/money.scala"), response.file.map(_.getName))
    assertEquals(
      "package billing\nfinal case class Money(currency: String, units: Long)\n",
      response.file.head.getContent
    )
  }

  @Test
  def pluginReportsUnsupportedParametersAsErrors(): Unit = {
    val request: CodeGeneratorRequest = CodeGeneratorRequest(
      fileToGenerate = Seq("billing/invoice.proto"),
      parameter = Some("grpc"),
      protoFile = Seq(moneyProto, invoiceProto)
    )

    val response: CodeGeneratorResponse = ModelPlugin.run(request.toByteArray)

    assertEquals(Some("unsupported parameter: grpc"), response.error)
    assertTrue(response.file.isEmpty)
  }

  @Test
  def extensionsSurviveSerializationAndUpdateLikeLenses(): Unit = {
    val options: FileOptions = invoiceProto.getOptions
    val wire: FileOptions = FileOptions.parseFrom(options.toByteArray)

    assertEquals(Some("com.acme.billing.java"), wire.javaPackage)
    assertEquals(options.extension(ScalapbProto.options), wire.extension(ScalapbProto.options))
    assertTrue(wire.unknownFields.getField(1020).isDefined)

    val nested: FileOptions = ScalapbProto.options.modify(_.map(_.withFlatPackage(false)))(wire)
    assertEquals(Some(false), nested.extension(ScalapbProto.options).flatMap(_.flatPackage))
    assertEquals(Some("com.acme.billing.model"),
      nested.extension(ScalapbProto.options).flatMap(_.packageName))
    assertEquals(Some(true), wire.extension(ScalapbProto.options).flatMap(_.flatPackage))

    val cleared: FileOptions = nested.withExtension(ScalapbProto.options)(None)
    assertEquals(None, cleared.extension(ScalapbProto.options))
    val clearedWire: FileOptions = FileOptions.parseFrom(cleared.toByteArray)
    assertEquals(None, clearedWire.unknownFields.getField(1020))
    assertEquals(FileOptions(javaPackage = Some("com.acme.billing.java")), clearedWire)

    val viaLens: FileOptions = cleared.update(_.javaMultipleFiles := true)
    val restored: FileOptions = ScalapbProto.options.set(
      Some(ScalaPbOptions().withPackageName("com.acme.billing.v2")))(viaLens)
    assertEquals(Some(true), restored.javaMultipleFiles)
    assertEquals(Some("com.acme.billing.v2"),
      FileOptions.parseFrom(restored.toByteArray).extension(ScalapbProto.options)
        .flatMap(_.packageName))
  }
}
