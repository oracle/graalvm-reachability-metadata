/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_3

import org.apache.pekko.http.ccompat.{pre213, since213}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.StaticAnnotation
import scala.jdk.CollectionConverters.*

class Pekko_parsing_3Test {
  @Test
  def annotationConstructorsCreateStaticAnnotations(): Unit = {
    val preAnnotation: StaticAnnotation = new pre213
    val sinceAnnotation: StaticAnnotation = new since213

    assertThat(preAnnotation).isNotNull
    assertThat(sinceAnnotation).isNotNull
    assertThat(preAnnotation == sinceAnnotation).isFalse
  }

  @Test
  def pre213AnnotationCanDecorateUsableScalaDeclarations(): Unit = {
    val tokenizer: Pre213Tokenizer = new Pre213Tokenizer("pre")

    assertThat(tokenizer.tokenize("alpha, beta,,gamma"))
      .containsExactly("pre:alpha", "pre:beta", "pre:gamma")
    assertThat(tokenizer.describe("payload")).isEqualTo("pre(payload)")
  }

  @Test
  def since213AnnotationCanDecorateUsableScalaDeclarations(): Unit = {
    val tokenizer: Since213Tokenizer = new Since213Tokenizer("since")

    assertThat(tokenizer.tokenize("delta|epsilon||zeta"))
      .containsExactly("since:delta", "since:epsilon", "since:zeta")
    assertThat(tokenizer.describe("payload")).isEqualTo("since(payload)")
  }

  @Test
  def compatibilityAnnotationsCanBeStackedOnTheSameDeclaration(): Unit = {
    val accumulator: CrossVersionAccumulator = new CrossVersionAccumulator("item")

    assertThat(accumulator.add("one").add("two").result()).isEqualTo("item=one;item=two")
  }

  @pre213
  private final class Pre213Tokenizer(private val prefix: String) {
    @pre213
    def tokenize(input: String): java.util.List[String] =
      input
        .split(",")
        .iterator
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(token => s"$prefix:$token")
        .toList
        .asJava

    @pre213
    def describe(value: String): String = s"$prefix($value)"
  }

  @since213
  private final class Since213Tokenizer(private val prefix: String) {
    @since213
    def tokenize(input: String): java.util.List[String] =
      input
        .split("\\|")
        .iterator
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(token => s"$prefix:$token")
        .toList
        .asJava

    @since213
    def describe(value: String): String = s"$prefix($value)"
  }

  @pre213
  @since213
  private final class CrossVersionAccumulator(private val key: String) {
    private val values: scala.collection.mutable.ArrayBuffer[String] = scala.collection.mutable.ArrayBuffer.empty

    @pre213
    @since213
    def add(value: String): CrossVersionAccumulator = {
      values += s"$key=$value"
      this
    }

    @since213
    @pre213
    def result(): String = values.mkString(";")
  }
}
