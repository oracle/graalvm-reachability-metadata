/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.ClassTag

final class ClassManifestDeprecatedApisTest {
  @Test
  def createsArrayManifestFromRuntimeClass(): Unit = {
    val stringTag: ClassTag[String] = ClassTag[String](classOf[String])
    val arrayTag: ClassTag[Array[String]] = stringTag.arrayManifest

    assertThat(arrayTag.runtimeClass).isEqualTo(classOf[Array[String]])
  }

  @Test
  def createsNestedArraysWithDeprecatedClassManifestApis(): Unit = {
    val stringTag: ClassTag[String] = ClassTag[String](classOf[String])

    val array2: Array[Array[String]] = stringTag.newArray2(2)
    val array3: Array[Array[Array[String]]] = stringTag.newArray3(3)
    val array4: Array[Array[Array[Array[String]]]] = stringTag.newArray4(4)
    val array5: Array[Array[Array[Array[Array[String]]]]] = stringTag.newArray5(5)

    assertThat(array2.length).isEqualTo(2)
    assertThat(array2.getClass).isEqualTo(classOf[Array[Array[String]]])
    assertThat(array3.length).isEqualTo(3)
    assertThat(array3.getClass).isEqualTo(classOf[Array[Array[Array[String]]]])
    assertThat(array4.length).isEqualTo(4)
    assertThat(array4.getClass).isEqualTo(classOf[Array[Array[Array[Array[String]]]]])
    assertThat(array5.length).isEqualTo(5)
    assertThat(array5.getClass).isEqualTo(classOf[Array[Array[Array[Array[Array[String]]]]]])
  }
}
