/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ghostdogpr.caliban_3

import caliban.Value.IntValue
import caliban.schema.Annotations.GQLField
import caliban.schema.Schema
import caliban.schema.Step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObjectSchemaTest {
  @Test
  def resolvesFieldDerivedFromPublicMethod(): Unit = {
    val schema: Schema[Any, ObjectSchemaMethodProbe] = Schema.derived[Any, ObjectSchemaMethodProbe]
    val step: Step[Any] = schema.resolve(ObjectSchemaMethodProbe(21))

    step match {
      case Step.ObjectStep(typeName, fields) =>
        assertThat(typeName).isEqualTo("ObjectSchemaMethodProbe")

        fields("doubled") match {
          case Step.PureStep(value: IntValue) => assertThat(value.toInt).isEqualTo(42)
          case other                         => throw new AssertionError(s"Expected pure IntValue step, got $other")
        }
      case other                             => throw new AssertionError(s"Expected ObjectStep, got $other")
    }
  }
}

case class ObjectSchemaMethodProbe(value: Int) {
  @GQLField
  def doubled: Int = value * 2
}
