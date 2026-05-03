/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package izumi.reflect

import _root_.izumi.reflect.thirdparty.internal.boopickle.NoMacro.*
import _root_.izumi.reflect.thirdparty.internal.boopickle.PickleImpl
import _root_.izumi.reflect.thirdparty.internal.boopickle.PickleState
import _root_.izumi.reflect.thirdparty.internal.boopickle.Pickler
import _root_.izumi.reflect.thirdparty.internal.boopickle.UnpickleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Izumi_reflect_thirdparty_boopickle_shaded_3Test {
  @Test
  def xmapDerivedPicklerRoundTripsDomainValue(): Unit = {
    final case class Label(value: String)

    implicit val labelPickler: Pickler[Label] = stringPickler.xmap[Label](Label.apply)((label: Label) => label.value)
    val value: Label = Label("xmap-derived domain value")

    val decoded: Label = roundTrip(value)

    assertEquals(value, decoded)
  }

  @Test
  def nestedCollectionPicklersRoundTripOptionsAndTuples(): Unit = {
    val value: Map[String, List[Option[(Int, Boolean)]]] = Map(
      "enabled" -> List(Some(1 -> true), None, Some(127 -> false)),
      "limits" -> List(Some((-4096, true)), Some((268435456, false)))
    )

    val decoded: Map[String, List[Option[(Int, Boolean)]]] = roundTrip(value)

    assertEquals(value, decoded)
  }

  private def roundTrip[A](value: A)(implicit pickler: Pickler[A]): A = {
    implicit val state: PickleState = PickleState.pickleStateSpeed
    val bytes = PickleImpl.intoBytes(value)
    UnpickleState(bytes).unpickle[A]
  }
}
