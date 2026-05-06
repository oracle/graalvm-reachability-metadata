/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import akka.protobufv3.internal.ListValue
import akka.protobufv3.internal.Value
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DescriptorMessageInfoFactoryTest {
  @Test
  def buildsSchemasForGeneratedMessagesWithOneofAndRepeatedMessageFields(): Unit = {
    val protobufClass: Class[?] = Class.forName("akka.protobufv3.internal.Protobuf")
    val schemaClass: Class[?] = Class.forName("akka.protobufv3.internal.Schema")
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

    assertNotNull(schemaFor.invokeWithArguments(protobuf, classOf[Value]))
    assertNotNull(schemaFor.invokeWithArguments(protobuf, classOf[ListValue]))
  }
}
