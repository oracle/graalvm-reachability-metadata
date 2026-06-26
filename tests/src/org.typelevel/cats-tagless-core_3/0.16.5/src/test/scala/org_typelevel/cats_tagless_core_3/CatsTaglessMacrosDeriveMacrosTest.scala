/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_tagless_core_3

import cats.~>
import cats.tagless.Derive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.experimental

trait TypedLookup[F[_]]:
  type Entity

  val description: F[String]

  def lookup(id: String): F[Entity]

@experimental
class CatsTaglessMacrosDeriveMacrosTest {
  @Test
  def derivedFunctorKPreservesAbstractTypeMembers(): Unit = {
    val source: TypedLookup[Option] { type Entity = Int } = new TypedLookup[Option]:
      type Entity = Int

      override val description: Option[String] = Some("numbers")

      override def lookup(id: String): Option[Entity] =
        Option.when(id == "one")(1)

    val optionToList: Option ~> List = new (Option ~> List):
      override def apply[A](fa: Option[A]): List[A] = fa.toList

    val transformed: TypedLookup[List] = Derive.functorK[TypedLookup].mapK(source)(optionToList)

    assertThat(transformed.description).isEqualTo(List("numbers"))
    assertThat(transformed.lookup("one")).isEqualTo(List(1))
    assertThat(transformed.lookup("missing")).isEqualTo(Nil)
  }
}
