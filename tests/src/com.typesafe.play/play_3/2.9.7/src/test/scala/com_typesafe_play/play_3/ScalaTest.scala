/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.util.Arrays

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.libs.{Scala => PlayScala}

class ScalaTest {
  @Test
  def convertsScalaSeqToJavaArrayWithRequestedElementType(): Unit = {
    val values: scala.collection.immutable.Seq[String] = PlayScala.toSeq(Arrays.asList("alpha", "beta"))

    val array: Array[String] = PlayScala.asArray(classOf[String], values)

    assertThat(array).containsExactly("alpha", "beta")
    assertThat(array.getClass.getComponentType).isEqualTo(classOf[String])
  }
}
