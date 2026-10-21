/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.ZipList
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats ZipList to combine weekly demand, staffing capacity, and overtime rates positionally.
  * The staffing report includes only the horizon for which all three forecasts are available.
  */
@Timeout(60)
class StaffingForecastZipListTest {
  private case class Demand(week: String, requiredHours: Int)

  private case class StaffingPlan(
    week: String,
    regularHours: Int,
    overtimeHours: Int,
    overtimeCents: Long
  )

  private def plan(
    demand: List[Demand],
    regularCapacity: List[Int],
    overtimeHourlyCents: List[Long]
  ): List[StaffingPlan] = {
    val combined: ZipList[StaffingPlan] = (
      ZipList(demand),
      ZipList(regularCapacity),
      ZipList(overtimeHourlyCents)
    ).mapN { (required, capacity, rate) =>
      val regular: Int = math.min(required.requiredHours, capacity)
      val overtime: Int = math.max(0, required.requiredHours - capacity)
      StaffingPlan(required.week, regular, overtime, overtime * rate)
    }
    combined.value
  }

  @Test
  def pairsEachWeeksDemandWithItsOwnCapacityAndRate(): Unit = {
    val demand: List[Demand] = List(
      Demand("week-1", 100),
      Demand("week-2", 70),
      Demand("week-3", 130)
    )
    val result: List[StaffingPlan] = plan(
      demand,
      List(80, 90, 100),
      List(3000L, 3200L, 3500L)
    )

    assertEquals(
      List(
        StaffingPlan("week-1", 80, 20, 60000L),
        StaffingPlan("week-2", 70, 0, 0L),
        StaffingPlan("week-3", 100, 30, 105000L)
      ),
      result
    )
    assertEquals(165000L, result.map(_.overtimeCents).sum)
  }

  @Test
  def limitsPublicationToTheSharedForecastHorizonWithoutRepeatingMissingValues(): Unit = {
    val demand: List[Demand] = List(
      Demand("week-1", 100),
      Demand("week-2", 70),
      Demand("week-3", 130)
    )
    val firstWeek: StaffingPlan = StaffingPlan("week-1", 80, 20, 60000L)

    assertEquals(
      List(firstWeek, StaffingPlan("week-2", 60, 10, 32000L)),
      plan(demand, List(80, 60), List(3000L, 3200L, 3500L))
    )
    assertEquals(
      List(firstWeek),
      plan(demand, List(80, 60, 100), List(3000L))
    )
    assertEquals(
      List(firstWeek),
      plan(demand.take(1), List(80, 60, 100), List(3000L, 3200L))
    )
    assertEquals(List.empty[StaffingPlan], plan(demand, List.empty, List(3000L)))
    assertEquals(List.empty[StaffingPlan], plan(demand, List(80), List.empty))
    assertEquals(List.empty[StaffingPlan], plan(List.empty, List(80), List(3000L)))
  }
}
