/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.collection.JavaConverters._
import scala.io.Source

final class SourceTest {
  @Test
  def readsResourceFromDefaultClassLoader(): Unit = {
    val source: Source = Source.fromResource("org_scala_lang/scala_library/source-from-resource.txt")

    try {
      assertThat(source.getLines().toList.asJava).containsExactly("alpha", "beta")
    } finally {
      source.close()
    }
  }
}
