/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.runtime.universe.TermName
import scala.reflect.runtime.universe.TermSymbol
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe.newTermName
import scala.reflect.runtime.universe.runtimeMirror

class JavaMirrorsInnerJavaMirrorInnerDerivedValueClassMetadataTest {
  @Test
  def fieldMirrorBoxesAndUnboxesDerivedValueClassValues(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val holder = new DerivedValueClassMetadataValueHolder(new DerivedValueClassMetadataMeter(12))
    val holderSymbol = mirror.staticClass(
      "org_scala_lang.scala_reflect.DerivedValueClassMetadataValueHolder"
    )
    val holderType = holderSymbol.toType
    val holderMirror = mirror.reflect(holder)
    val fieldMirror = holderMirror.reflectField(singleTerm(holderType, newTermName("distance")))

    val reflectedValue = fieldMirror.get.asInstanceOf[DerivedValueClassMetadataMeter]
    assertThat(reflectedValue.value).isEqualTo(12)

    fieldMirror.set(new DerivedValueClassMetadataMeter(27))
    assertThat(holder.distance.value).isEqualTo(27)
  }

  private def singleTerm(ownerType: Type, name: TermName): TermSymbol =
    ownerType.decl(name).asTerm
}

final class DerivedValueClassMetadataMeter(val value: Int) extends AnyVal

final class DerivedValueClassMetadataValueHolder(var distance: DerivedValueClassMetadataMeter)
