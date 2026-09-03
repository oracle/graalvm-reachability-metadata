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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TypesInnerTypeVariableInvocationHandlerTest {
  @Test
  def artificialTypeVariablesDelegateSupportedMethodsToBackingImplementation(): Unit = {
    val variable: TypeVariable[?] = newArtificialTypeVariable(
      classOf[TypesInnerTypeVariableInvocationHandlerTest],
      "Synthetic",
      classOf[Number]
    )

    assertEquals("Synthetic", variable.getName)
    assertSame(classOf[TypesInnerTypeVariableInvocationHandlerTest], variable.getGenericDeclaration)
    assertEquals("Synthetic", variable.toString)
  }

  private def newArtificialTypeVariable(
      declaration: GenericDeclaration,
      name: String,
      bounds: Type*
  ): TypeVariable[?] = {
    val newArtificialTypeVariable: Method = Class
      .forName("org.joda.convert.Types")
      .getDeclaredMethod(
        "newArtificialTypeVariable",
        classOf[GenericDeclaration],
        classOf[String],
        classOf[Array[Type]]
      )
    newArtificialTypeVariable.setAccessible(true)

    newArtificialTypeVariable
      .invoke(
        null,
        declaration.asInstanceOf[Object],
        name,
        bounds.toArray.asInstanceOf[Object]
      )
      .asInstanceOf[TypeVariable[?]]
  }
}
