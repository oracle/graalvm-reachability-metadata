/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.libs.reflect.MethodUtils

class MethodUtilsTest {
  @Test
  def findsDirectPublicMethodByExactSignature(): Unit = {
    val method: Method = MethodUtils.getMatchingAccessibleMethod(
      classOf[DirectMethodTarget],
      "exact",
      classOf[String]
    )

    assertThat(method).isNotNull
    assertThat(method.invoke(new DirectMethodTarget, "play")).isEqualTo("exact:play")
  }

  @Test
  def scansPublicMethodsForAssignableSignature(): Unit = {
    val method: Method = MethodUtils.getMatchingAccessibleMethod(
      classOf[AssignableMethodTarget],
      "accept",
      classOf[String]
    )

    assertThat(method).isNotNull
    assertThat(method.invoke(new AssignableMethodTarget, "framework"))
      .isEqualTo("accepted:framework")
  }

  @Test
  def resolvesAccessibleMethodFromPublicInterfaceImplementedByNonPublicClass(): Unit = {
    val list: java.util.List[String] = Collections.unmodifiableList(new ArrayList[String]())
    val method: Method = list.getClass.getMethod("size")

    val accessibleMethod: Method = MethodUtils.getAccessibleMethod(method)

    assertThat(accessibleMethod).isNotNull
    assertThat(Modifier.isPublic(accessibleMethod.getDeclaringClass.getModifiers)).isTrue
    assertThat(accessibleMethod.invoke(list)).isEqualTo(0)
  }

  @Test
  def resolvesAccessibleMethodFromPublicSuperclassOfNonPublicClass(): Unit = {
    val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
    val method: Method = encoder.getClass.getMethod("canEncode", java.lang.Character.TYPE)

    val accessibleMethod: Method = MethodUtils.getAccessibleMethod(method)

    assertThat(accessibleMethod).isNotNull
    assertThat(accessibleMethod.getDeclaringClass).isEqualTo(classOf[CharsetEncoder])
    assertThat(accessibleMethod.invoke(encoder, Character.valueOf('A'))).isEqualTo(true)
  }
}

class DirectMethodTarget {
  def exact(value: String): String = s"exact:$value"
}

class AssignableMethodTarget {
  def accept(value: CharSequence): String = s"accepted:$value"
}

