/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala BigDecimal data binding for exact payment reconciliation. */
@Timeout(60)
class PaymentReconciliationTest {
  @Test
  def reconcilesSettlementWithoutFloatingPointRounding(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // The opening balance exceeds the range where a Double can retain cents.
    val openingBalance: BigDecimal = mapper.readValue[BigDecimal]("9007199254740993.01")
    val entries: List[BigDecimal] = mapper.readValue[List[BigDecimal]](
      "[19.99,-0.10,-2.34,0]")
    assertEquals(BigDecimal("9007199254740993.01"), openingBalance)
    assertEquals(List(BigDecimal("19.99"), BigDecimal("-0.10"),
      BigDecimal("-2.34"), BigDecimal(0)), entries)

    val netSettlement: BigDecimal = entries.sum
    val closingBalance: BigDecimal = openingBalance + netSettlement
    assertEquals(BigDecimal("17.55"), netSettlement)
    assertEquals(BigDecimal("9007199254741010.56"), closingBalance)

    // Publish a numeric amount, not a string, for the next reconciliation run.
    val published: String = mapper.writeValueAsString(closingBalance)
    assertTrue(mapper.readTree(published).isNumber)
    val restored: BigDecimal = mapper.readValue[BigDecimal](published)
    assertEquals(closingBalance, restored)
    assertEquals(netSettlement, restored - openingBalance)

    val publishedEntries: String = mapper.writeValueAsString(entries)
    val restoredEntries: List[BigDecimal] =
      mapper.readValue[List[BigDecimal]](publishedEntries)
    assertEquals(entries, restoredEntries)
    assertEquals(restored, openingBalance + restoredEntries.sum)
  }
}
