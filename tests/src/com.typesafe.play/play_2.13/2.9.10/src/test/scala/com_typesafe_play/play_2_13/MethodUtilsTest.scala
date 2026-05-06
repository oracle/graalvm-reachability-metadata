/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.lang.reflect.Method

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.libs.reflect.MethodUtils

class MethodUtilsTest {
  @Test
  def findsAccessibleInterfaceMethodForNonPublicImplementation(): Unit = {
    val implementationMethod: Method = classOf[PrivateInterfaceImplementation]
      .getMethod("greet", classOf[String])

    val accessibleMethod: Method = MethodUtils.getAccessibleMethod(implementationMethod)

    assertThat(accessibleMethod).isNotNull
    assertThat(accessibleMethod.getDeclaringClass).isEqualTo(classOf[PublicMethodUtilsGreeting])
    assertThat(accessibleMethod.getName).isEqualTo("greet")
  }

  @Test
  def findsAccessibleSuperclassMethodForNonPublicSubclass(): Unit = {
    val implementationMethod: Method = classOf[PrivateSuperclassImplementation]
      .getMethod("inheritedGreeting", classOf[String])

    val accessibleMethod: Method = MethodUtils.getAccessibleMethod(implementationMethod)

    assertThat(accessibleMethod).isNotNull
    assertThat(accessibleMethod.getDeclaringClass).isEqualTo(classOf[PublicMethodUtilsSuperclass])
    assertThat(accessibleMethod.getName).isEqualTo("inheritedGreeting")
  }

  @Test
  def returnsExactPublicMethodWhenParameterTypesMatch(): Unit = {
    val method: Method = MethodUtils.getMatchingAccessibleMethod(
      classOf[ExactMatchingMethodTarget],
      "echo",
      classOf[String]
    )

    assertThat(method).isNotNull
    assertThat(method.getDeclaringClass).isEqualTo(classOf[ExactMatchingMethodTarget])
    assertThat(method.getParameterTypes.length).isEqualTo(1)
    assertThat(method.getParameterTypes()(0)).isEqualTo(classOf[String])
  }

  @Test
  def scansPublicMethodsToFindAssignableParameterMatch(): Unit = {
    val method: Method = MethodUtils.getMatchingAccessibleMethod(
      classOf[AssignableMatchingMethodTarget],
      "describe",
      classOf[Integer]
    )

    assertThat(method).isNotNull
    assertThat(method.getDeclaringClass).isEqualTo(classOf[AssignableMatchingMethodTarget])
    assertThat(method.getParameterTypes.length).isEqualTo(1)
    assertThat(method.getParameterTypes()(0)).isEqualTo(classOf[Number])
  }

  private final class PrivateInterfaceImplementation extends PublicMethodUtilsGreeting {
    override def greet(name: String): String = s"hello $name"
  }

  private final class PrivateSuperclassImplementation extends PublicMethodUtilsSuperclass {
    override def inheritedGreeting(name: String): String = s"subclass $name"
  }
}

trait PublicMethodUtilsGreeting {
  def greet(name: String): String
}

class PublicMethodUtilsSuperclass {
  def inheritedGreeting(name: String): String = s"superclass $name"
}

class ExactMatchingMethodTarget {
  def echo(value: String): String = value
}

class AssignableMatchingMethodTarget {
  def describe(value: Number): String = s"number $value"

  def describe(value: AnyRef): String = s"object $value"
}
