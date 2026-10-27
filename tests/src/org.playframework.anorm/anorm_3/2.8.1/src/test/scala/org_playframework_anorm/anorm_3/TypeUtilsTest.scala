/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import org.joda.convert.StringConvert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypeUtilsTest {
  @Test
  def parsesJavaSourceStyleArrayTypeArguments(): Unit = {
    val parsed: Type = new StringConvert().convertFromString(
      classOf[Type],
      "java.util.List<java.lang.String[]>"
    )

    val parameterized: ParameterizedType = asParameterizedType(parsed)
    assertEquals(classOf[java.util.List[?]], parameterized.getRawType)
    assertEquals(classOf[Array[String]], parameterized.getActualTypeArguments.apply(0))
  }

  @Test
  def parsesJvmDescriptorStyleArrayTypeArguments(): Unit = {
    val parsed: Type = new StringConvert().convertFromString(
      classOf[Type],
      "java.util.List<[Ljava.lang.String;>"
    )

    val parameterized: ParameterizedType = asParameterizedType(parsed)
    assertEquals(classOf[java.util.List[?]], parameterized.getRawType)
    assertEquals(classOf[Array[String]], parameterized.getActualTypeArguments.apply(0))
  }

  private def asParameterizedType(parsed: Type): ParameterizedType = {
    assertTrue(
      parsed.isInstanceOf[ParameterizedType],
      s"Expected a parameterized type but got ${parsed.getTypeName}"
    )
    parsed.asInstanceOf[ParameterizedType]
  }
}
