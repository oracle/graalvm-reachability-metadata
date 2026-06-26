/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_tagless_core_3

import cats.Id
import cats.tagless.Derive
import cats.tagless.aop.Aspect
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.experimental

trait Evidence[A]:
  def label: String

trait RuntimeAspectAlgebra[F[_]]:
  def describe(value: Int)(using Evidence[String]): F[String]

@experimental
class CatsTaglessMacrosMacroAspectTest {
  @Test
  def derivedAspectWeavesMethodsWithGivenEvidence(): Unit = {
    val algebra: RuntimeAspectAlgebra[Id] = new RuntimeAspectAlgebra[Id]:
      override def describe(value: Int)(using Evidence[String]): String = s"value-$value"

    given Evidence[Int] with
      override def label: String = "integer-argument"

    given Evidence[String] with
      override def label: String = "method-result"

    val aspect: Aspect[RuntimeAspectAlgebra, Evidence, Evidence] =
      Derive.aspect[RuntimeAspectAlgebra, Evidence, Evidence]
    val woven: RuntimeAspectAlgebra[[X] =>> Aspect.Weave[Id, Evidence, Evidence, X]] =
      aspect.weave(algebra)

    val result: Aspect.Weave[Id, Evidence, Evidence, String] = woven.describe(7)

    assertThat(result.instrumentation.value).isEqualTo("value-7")
    assertThat(result.instrumentation.algebraName).contains("RuntimeAspectAlgebra")
    assertThat(result.instrumentation.methodName).isEqualTo("describe")
  }
}
