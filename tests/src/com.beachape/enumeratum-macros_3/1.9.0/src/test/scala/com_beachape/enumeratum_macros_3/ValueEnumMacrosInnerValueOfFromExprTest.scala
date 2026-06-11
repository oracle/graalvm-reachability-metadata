/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

import enumeratum.values.IntEnum
import enumeratum.values.IntEnumEntry
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*
import java.lang.reflect.InvocationTargetException
import scala.quoted.Expr
import scala.quoted.Quotes
import scala.quoted.Type
import scala.quoted.staging.Compiler
import scala.quoted.staging.run

sealed abstract class ValueOfFromExprIntEntry(val value: Int) extends IntEnumEntry

case object ValueOfFromExprFirst extends ValueOfFromExprIntEntry(1)
case object ValueOfFromExprSecond extends ValueOfFromExprIntEntry(2)

object ValueOfFromExprIntEntry extends IntEnum[ValueOfFromExprIntEntry] {
  val values: IndexedSeq[ValueOfFromExprIntEntry] = findValues
}

class ValueEnumMacrosInnerValueOfFromExprTest {
  @Test
  def discoversSingletonValueEntriesThroughTheMacroApi(): Unit = {
    assertThat(ValueOfFromExprIntEntry.values.asJava)
      .containsExactly(ValueOfFromExprFirst, ValueOfFromExprSecond)
    assertThat(ValueOfFromExprIntEntry.values.map(_.value).asJava).containsExactly(1, 2)
    assertThat(ValueOfFromExprIntEntry.withValue(2)).isSameAs(ValueOfFromExprSecond)
  }

  @Test
  def stagedValueOfExtractionLoadsSingletonModuleFields(): Unit = {
    try {
      given Compiler = Compiler.make(Thread.currentThread.getContextClassLoader)

      val extractedValue: Int = run {
        type SingletonEntry = ValueOfFromExprFirst.type
        val quotes = summon[Quotes]
        val expression: Expr[ValueOf[SingletonEntry]] =
          '{ new ValueOf[SingletonEntry](ValueOfFromExprFirst) }
        val extractorClass = Class.forName("enumeratum.ValueEnumMacros$ValueOfFromExpr")
        val extractor = extractorClass.getConstructor(classOf[Type[?]]).newInstance(Type.of[SingletonEntry])
        val result = extractorClass
          .getMethod("unapply", classOf[Expr[?]], classOf[Quotes])
          .invoke(extractor, expression, quotes)
          .asInstanceOf[Option[ValueOf[SingletonEntry]]]

        Expr(result.map(_.value.value).getOrElse(-1))
      }

      assertThat(extractedValue).isEqualTo(1)
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
      case exception: InvocationTargetException
          if exception.getCause.isInstanceOf[Error] &&
            NativeImageSupport.isUnsupportedFeatureError(exception.getCause.asInstanceOf[Error]) =>
    }
  }
}
