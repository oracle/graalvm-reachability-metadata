/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3

import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto
import org.apache.pekko.protobufv3.internal.Descriptors.Descriptor
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

import scala.annotation.static

class ExtensionSchemasTest {
  @Test
  def loadsFullRuntimeExtensionSchemaForProto2GeneratedMessages(): Unit = {
    assertDoesNotThrow(new Executable {
      override def execute(): Unit = ExtensionSchemasProto2Host.newMutable().parseEmptyInputWithGeneratedMessageSchema()
    })

    assertSame(
      ExtensionSchemasProto2Host.getDefaultInstance(),
      ExtensionSchemasProto2Host.getDefaultInstance().getDefaultInstanceForType()
    )
  }
}

class ExtensionSchemasProto2Host private () extends SchemaBackedGeneratedMessage {
  override protected def internalGetFieldAccessorTable(): GeneratedMessageV3.FieldAccessorTable =
    ExtensionSchemasProto2Host.FieldAccessorTable
}

object ExtensionSchemasProto2Host {
  private val File: FileDescriptor = {
    val hostType: DescriptorProto = DescriptorProto.newBuilder()
      .setName("ExtensionSchemasProto2Host")
      .build()
    val fileProto: FileDescriptorProto = FileDescriptorProto.newBuilder()
      .setName("extension_schemas_proto2_host.proto")
      .setPackage("dynamicaccess.extensionschemas")
      .setSyntax("proto2")
      .addMessageType(hostType)
      .build()

    FileDescriptor.buildFrom(fileProto, Array.empty[FileDescriptor])
  }

  private val HostDescriptor: Descriptor = File.findMessageTypeByName("ExtensionSchemasProto2Host")
  val FieldAccessorTable: GeneratedMessageV3.FieldAccessorTable =
    new GeneratedMessageV3.FieldAccessorTable(HostDescriptor, Array.empty)

  private val DefaultInstance: ExtensionSchemasProto2Host = new ExtensionSchemasProto2Host()

  @static def getDefaultInstance(): ExtensionSchemasProto2Host = DefaultInstance

  def newMutable(): ExtensionSchemasProto2Host = new ExtensionSchemasProto2Host()
}
