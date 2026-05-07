/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.lang.reflect.GenericDeclaration
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypesTest {
  @Test
  def createsArrayClassForComponentType(): Unit = {
    val getArrayClass: Method = Class.forName("org.joda.convert.Types")
      .getDeclaredMethod("getArrayClass", classOf[Class[?]])
    getArrayClass.setAccessible(true)

    val arrayClass: Object = getArrayClass.invoke(null, classOf[String])

    assertThat(arrayClass).isSameAs(classOf[Array[String]])
  }

  @Test
  def createsArtificialTypeVariableProxy(): Unit = {
    val variable: TypeVariable[?] = newArtificialTypeVariable(
      classOf[TypesTest],
      "Synthetic",
      classOf[Number]
    )

    assertThat(variable.getName).isEqualTo("Synthetic")
    assertThat(variable.getGenericDeclaration).isSameAs(classOf[TypesTest])
    assertThat(variable.toString).isEqualTo("Synthetic")
  }

  private def newArtificialTypeVariable(
      declaration: GenericDeclaration,
      name: String,
      bounds: Type*
  ): TypeVariable[?] = {
    val newArtificialTypeVariable: Method = Class.forName("org.joda.convert.Types")
      .getDeclaredMethod(
        "newArtificialTypeVariable",
        classOf[GenericDeclaration],
        classOf[String],
        classOf[Array[Type]]
      )
    newArtificialTypeVariable.setAccessible(true)

    newArtificialTypeVariable.invoke(null, declaration, name, bounds.toArray)
      .asInstanceOf[TypeVariable[?]]
  }
}
