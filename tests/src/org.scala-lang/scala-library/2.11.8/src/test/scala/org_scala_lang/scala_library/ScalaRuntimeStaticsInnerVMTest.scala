/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

final class ScalaRuntimeStaticsInnerVMTest {
  @Test
  def locatesUnsafeInstanceUsedByReleaseFenceFallback(): Unit = {
    val vmClass: Class[_] = Class.forName(ScalaRuntimeStaticsInnerVMTest.VmClassName, false, getClass.getClassLoader)
    val unsafeClass: Class[_] = Class.forName(ScalaRuntimeStaticsInnerVMTest.UnsafeClassName)
    val lookup: MethodHandles.Lookup = MethodHandles.privateLookupIn(vmClass, MethodHandles.lookup())
    val findUnsafe: MethodHandle = lookup.findStatic(
      vmClass,
      "findUnsafe",
      MethodType.methodType(classOf[Object], classOf[Class[_]])
    )

    val unsafe: AnyRef = findUnsafe.invokeWithArguments(unsafeClass).asInstanceOf[AnyRef]

    assertThat(unsafe).isNotNull
    assertThat(unsafeClass.isInstance(unsafe)).isTrue
  }
}

private object ScalaRuntimeStaticsInnerVMTest {
  val VmClassName: String = "scala.runtime.Statics$VM"
  val UnsafeClassName: String = "sun.misc.Unsafe"
}
