/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_2_13

import org.assertj.core.api.Assertions.assertThat
import org.json4s.reflect.Executable
import org.json4s.reflect.ScalaType
import org.json4s.reflect.SingletonDescriptor
import org.junit.jupiter.api.Test

class ExecutableTest {
  @Test
  def invokesWrappedMethodOnDescriptorInstance(): Unit = {
    val fixture: ExecutableInvocationFixture = new ExecutableInvocationFixture
    val method: java.lang.reflect.Method = classOf[ExecutableInvocationFixture]
      .getMethod("decorate", classOf[String], classOf[java.lang.Integer])
    val descriptor: SingletonDescriptor = SingletonDescriptor(
      simpleName = "ExecutableInvocationFixture",
      fullName = classOf[ExecutableInvocationFixture].getName,
      erasure = ScalaType(classOf[ExecutableInvocationFixture]),
      instance = fixture,
      properties = Seq.empty
    )

    val result: Any = new Executable(method).invoke(Some(descriptor), Seq("item", Integer.valueOf(12)))

    assertThat(result).isEqualTo("item-12")
  }

  @Test
  def invokesWrappedConstructorWithArguments(): Unit = {
    val constructor: java.lang.reflect.Constructor[ExecutableConstructedFixture] = classOf[ExecutableConstructedFixture]
      .getConstructor(classOf[String], classOf[java.lang.Integer])

    val result: ExecutableConstructedFixture = new Executable(constructor, isPrimaryCtor = true)
      .invoke(None, Seq("created", Integer.valueOf(5)))
      .asInstanceOf[ExecutableConstructedFixture]

    assertThat(result.description).isEqualTo("created:5")
  }
}

class ExecutableInvocationFixture {
  def decorate(prefix: String, count: java.lang.Integer): String = s"$prefix-$count"
}

class ExecutableConstructedFixture(val name: String, val count: java.lang.Integer) {
  def description: String = s"$name:$count"
}
