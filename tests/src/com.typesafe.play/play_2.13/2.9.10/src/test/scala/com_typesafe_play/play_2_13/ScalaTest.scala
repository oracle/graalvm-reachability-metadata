/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.util.Arrays

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScalaTest {
  @Test
  def convertsScalaSeqToTypedJavaArray(): Unit = {
    val values: scala.collection.immutable.Seq[String] = _root_.play.libs.Scala.toSeq(
      Arrays.asList("alpha", "beta", "gamma")
    )

    val array: Array[String] = _root_.play.libs.Scala.asArray(classOf[String], values)

    assertThat(array).containsExactly("alpha", "beta", "gamma")
    assertThat(array.getClass.getComponentType).isEqualTo(classOf[String])
  }
}
