/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.Eval
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats Eval to calculate manufacturing costs through nested bills of materials.
  * Deferred recursion lets the same costing workflow handle both small kits and deep assemblies.
  */
@Timeout(60)
class ManufacturingCostEvalTest {
  private sealed trait Component

  private case class Material(sku: String, quantity: Int, unitCents: Long) extends Component

  private case class Assembly(sku: String, quantity: Int, contents: List[Component])
    extends Component

  private def cost(component: Component): Eval[Long] = Eval.defer {
    component match {
      case Material(_, quantity, unitCents) => Eval.now(quantity * unitCents)
      case Assembly(_, quantity, contents) =>
        contents.traverse(cost).map(_.sum * quantity)
    }
  }

  @Test
  def rollsUpMaterialQuantitiesAtEveryAssemblyLevel(): Unit = {
    val shelf: Component = Assembly(
      "SHELF",
      2,
      List(Material("SCREW", 4, 5L), Material("PANEL", 1, 800L))
    )
    val packagedKit: Component = Assembly(
      "KIT",
      1,
      List(shelf, Material("CARTON", 1, 100L))
    )
    val productionOrder: Component = Assembly("ORDER", 3, List(packagedKit))
    val quotation: Eval[Long] = cost(productionOrder)

    assertEquals(1640L, cost(shelf).value)
    assertEquals(1740L, cost(packagedKit).value)
    assertEquals(5220L, quotation.value)
    assertEquals(5220L, quotation.value)
    assertEquals(0L, cost(Assembly("EMPTY", 1, List.empty)).value)
  }

  @Test
  def costsADeepImportedAssemblyHierarchyWithoutRecursiveStackGrowth(): Unit = {
    val levels: Int = 20000
    val base: Component = Material("CORE", 1, 450L)
    val imported: Component = (1 to levels).foldLeft(base) { (child, level) =>
      Assembly(s"ASSEMBLY-$level", 1, List(Material("FASTENER", 2, 1L), child))
    }

    val quotedCents: Long = cost(imported).value

    assertEquals(40450L, quotedCents)
  }
}
