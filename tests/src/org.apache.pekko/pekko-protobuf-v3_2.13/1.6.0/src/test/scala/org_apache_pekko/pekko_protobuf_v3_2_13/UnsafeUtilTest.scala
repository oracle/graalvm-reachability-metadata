/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13

import java.io.ByteArrayOutputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import org.apache.pekko.protobufv3.internal.ByteString
import org.apache.pekko.protobufv3.internal.CodedOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnsafeUtilTest {
  @Test
  def codedOutputStreamInitializesUnsafeSupport(): Unit = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: CodedOutputStream = CodedOutputStream.newInstance(bytes)

    output.writeBytesNoTag(ByteString.copyFromUtf8("pekko"))
    output.flush()

    assertThat(bytes.toByteArray).containsExactly(
      5.toByte,
      'p'.toByte,
      'e'.toByte,
      'k'.toByte,
      'k'.toByte,
      'o'.toByte
    )
  }

  @Test
  def allocateInstanceCreatesObjectWithoutCallingConstructor(): Unit = {
    val unsafeUtilClass: Class[_] = Class.forName(
      "org.apache.pekko.protobufv3.internal.UnsafeUtil",
      true,
      classOf[ByteString].getClassLoader
    )
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(
      unsafeUtilClass,
      MethodHandles.lookup()
    )
    val allocateInstance: MethodHandle = lookup.findStatic(
      unsafeUtilClass,
      "allocateInstance",
      methodType(classOf[Object], classOf[Class[_]])
    )

    val instance: ConstructorGuardedType = allocateInstance
      .invokeWithArguments(classOf[ConstructorGuardedType])
      .asInstanceOf[ConstructorGuardedType]

    assertThat(instance.constructorValue).isZero()
  }
}

final class ConstructorGuardedType private () {
  private val constructedValue: Int = throw new AssertionError(
    "Unsafe allocation must not invoke constructors"
  )

  def constructorValue: Int = constructedValue
}
