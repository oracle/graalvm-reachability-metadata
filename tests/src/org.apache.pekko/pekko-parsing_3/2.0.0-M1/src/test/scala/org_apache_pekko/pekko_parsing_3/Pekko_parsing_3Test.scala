/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_3

import org.apache.pekko.http.ccompat
import org.apache.pekko.http.ccompat.MapHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class Pekko_parsing_3Test {
  @Test
  def mapHelpersConvertJavaMapToImmutableScalaMap(): Unit = {
    val source: java.util.LinkedHashMap[String, Integer] = new java.util.LinkedHashMap[String, Integer]
    source.put("host", Integer.valueOf(1))
    source.put("port", Integer.valueOf(8080))

    val converted: Map[String, Integer] = MapHelpers.convertMapToScala(source)

    assertThat(converted("host")).isEqualTo(Integer.valueOf(1))
    assertThat(converted("port")).isEqualTo(Integer.valueOf(8080))
    assertThat(converted.asJava).hasSize(2)
  }

  @Test
  def convertedScalaMapIsIndependentFromSourceJavaMap(): Unit = {
    val source: java.util.LinkedHashMap[String, String] = new java.util.LinkedHashMap[String, String]
    source.put("content-type", "text/plain")

    val converted: Map[String, String] = MapHelpers.convertMapToScala(source)
    source.put("content-length", "12")

    assertThat(converted.asJava).containsEntry("content-type", "text/plain")
    assertThat(converted.asJava).doesNotContainKey("content-length")
  }

  @Test
  def ccompatBuilderAliasBuildsScalaCollections(): Unit = {
    val builder: ccompat.Builder[String, Vector[String]] = Vector.newBuilder[String]
    builder += "alpha"
    builder += "beta"

    val result: Vector[String] = builder.result()

    assertThat(result.asJava).containsExactly("alpha", "beta")
  }
}
