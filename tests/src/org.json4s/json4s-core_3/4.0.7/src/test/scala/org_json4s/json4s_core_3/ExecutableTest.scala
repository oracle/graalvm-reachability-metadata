/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_3

import org.json4s.reflect.Executable
import org.json4s.reflect.Reflector
import org.json4s.reflect.SingletonDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecutableMethodSubject {
  def render(label: String, amount: java.lang.Integer): String = s"$label:$amount"
}

class ExecutableConstructorSubject(val label: String, val amount: java.lang.Integer) {
  def rendered: String = s"$label:$amount"
}

class ExecutableTest {
  @Test
  def invokesWrappedMethodOnCompanionInstance(): Unit = {
    val subject: ExecutableMethodSubject = new ExecutableMethodSubject
    val executable: Executable = new Executable(
      classOf[ExecutableMethodSubject].getMethod("render", classOf[String], classOf[java.lang.Integer])
    )
    val singletonDescriptor: SingletonDescriptor = SingletonDescriptor(
      simpleName = "ExecutableMethodSubject",
      fullName = classOf[ExecutableMethodSubject].getName,
      erasure = Reflector.scalaTypeOf[ExecutableMethodSubject],
      instance = subject,
      properties = Seq.empty
    )

    val rendered: Any = executable.invoke(Some(singletonDescriptor), Seq("items", java.lang.Integer.valueOf(7)))

    assertEquals("items:7", rendered)
  }

  @Test
  def invokesWrappedConstructorWithArguments(): Unit = {
    val executable: Executable = new Executable(
      classOf[ExecutableConstructorSubject].getConstructor(classOf[String], classOf[java.lang.Integer]),
      true
    )

    val constructed: ExecutableConstructorSubject = executable
      .invoke(None, Seq("orders", java.lang.Integer.valueOf(3)))
      .asInstanceOf[ExecutableConstructorSubject]

    assertEquals("orders:3", constructed.rendered)
  }
}
