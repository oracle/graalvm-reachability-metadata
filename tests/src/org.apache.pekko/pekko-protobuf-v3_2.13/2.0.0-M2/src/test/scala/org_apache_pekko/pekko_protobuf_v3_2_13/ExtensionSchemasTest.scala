/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import org.apache.pekko.protobufv3.internal.DescriptorProtos
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtensionSchemasTest {
  @Test
  def fullRuntimeExtensionSchemaIsLoadedForProto2Messages(): Unit = {
    val message: ExtensionSchemasProto2Message = ExtensionSchemasProto2Message.getDefaultInstance

    message.parseEmptyInputThroughSchema()

    assertThat(message.getDescriptorForType.getFile.toProto.getSyntax).isEqualTo("proto2")
  }

  @Test
  def fullRuntimeSchemaFactoryLoadsExtensionSchemaFullConstructor(): Unit = {
    val protobufClass: Class[?] = Class.forName("org.apache.pekko.protobufv3.internal.Protobuf")
    val schemaClass: Class[?] = Class.forName("org.apache.pekko.protobufv3.internal.Schema")
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      protobufClass,
      MethodHandles.lookup()
    )
    val getInstance: MethodHandle = lookup.findStatic(
      protobufClass,
      "getInstance",
      methodType(protobufClass)
    )
    val schemaFor: MethodHandle = lookup.findVirtual(
      protobufClass,
      "schemaFor",
      methodType(schemaClass, classOf[Class[_]])
    )
    val protobuf: Object = getInstance.invokeWithArguments()
    val schema: Object = schemaFor.invokeWithArguments(
      protobuf,
      classOf[DescriptorProtos.FileDescriptorSet]
    )

    assertThat(schema).isNotNull
  }
}
