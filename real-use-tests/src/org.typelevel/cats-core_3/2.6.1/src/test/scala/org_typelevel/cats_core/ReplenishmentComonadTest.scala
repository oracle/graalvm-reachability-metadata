/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.Comonad
import cats.data.NonEmptyList
import cats.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Comonad on NonEmptyList to plan rolling inventory replenishment targets.
  * Each day's target covers the next three forecast days plus a fixed safety stock.
  */
@Timeout(60)
class ReplenishmentComonadTest {
  private case class Demand(day: String, units: Int)

  private case class Target(day: String, forecastDays: Int, stockUnits: Int)

  private def targetForWindow(forecast: NonEmptyList[Demand]): Target = {
    val current: Demand = Comonad[NonEmptyList].extract(forecast)
    val window: List[Demand] = forecast.toList.take(3)
    val safetyStock: Int = 5
    Target(current.day, window.size, window.map(_.units).sum + safetyStock)
  }

  private def replenishmentPlan(forecast: List[Demand]): List[Target] = {
    NonEmptyList.fromList(forecast).map { days =>
      Comonad[NonEmptyList].coflatMap(days)(targetForWindow).toList
    }.getOrElse(List.empty)
  }

  @Test
  def calculatesADistinctForwardLookingTargetForEveryForecastDay(): Unit = {
    val forecast: List[Demand] = List(
      Demand("Monday", 12),
      Demand("Tuesday", 0),
      Demand("Wednesday", 7),
      Demand("Thursday", 20),
      Demand("Friday", 3)
    )
    val expected: List[Target] = List(
      Target("Monday", 3, 24),
      Target("Tuesday", 3, 32),
      Target("Wednesday", 3, 35),
      Target("Thursday", 2, 28),
      Target("Friday", 1, 8)
    )

    assertEquals(expected, replenishmentPlan(forecast))
    assertEquals(expected.drop(2), replenishmentPlan(forecast.drop(2)))
  }

  @Test
  def usesAvailableForecastDaysAtTheHorizonAndRetainsSafetyStockOnQuietDays(): Unit = {
    assertEquals(
      List(Target("Saturday", 2, 9), Target("Sunday", 1, 5)),
      replenishmentPlan(List(Demand("Saturday", 4), Demand("Sunday", 0)))
    )
    assertEquals(
      List(Target("Sunday", 1, 5)),
      replenishmentPlan(List(Demand("Sunday", 0)))
    )
    assertEquals(List.empty[Target], replenishmentPlan(List.empty))
  }
}
