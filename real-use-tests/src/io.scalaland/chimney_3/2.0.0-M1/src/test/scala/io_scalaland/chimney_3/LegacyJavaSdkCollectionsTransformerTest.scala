/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.javacollections.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.time.DayOfWeek
import java.util.ArrayList as JArrayList
import java.util.Arrays
import java.util.EnumSet
import java.util.LinkedHashMap as JLinkedHashMap
import java.util.List as JList
import java.util.Optional
import java.util.Properties
import java.util.TreeMap
import java.util.stream.Stream

/**
 * Conversions between Scala types and `java.util` collections, `Optional` and boxed primitives.
 *
 * A warehouse service written in Scala talks to a legacy inventory SDK whose DTOs are built from
 * `java.util` collections, `java.util.Optional` and boxed numbers. Importing the module's
 * implicits lets the same derivations used for Scala types map between `List` and `ArrayList`,
 * `Map` and `TreeMap`, `Set` and `EnumSet`, `Option` and `Optional`, and `Int` and `Integer`, in
 * both directions. Reading data back from the SDK is partial when a required value arrives as an
 * empty `Optional`, and the failure reports the path of the missing value.
 */
class LegacyJavaSdkCollectionsTransformerTest {

  final case class Bin(label: String, capacity: Int, fragile: Boolean)
  final case class Warehouse(
      code: String,
      bins: List[Bin],
      stock: Map[String, Int],
      manager: Option[String],
      openDays: Set[DayOfWeek],
      tags: Vector[String]
  )

  final case class SdkBin(label: String, capacity: java.lang.Integer, fragile: java.lang.Boolean)
  final case class SdkWarehouse(
      code: String,
      bins: JArrayList[SdkBin],
      stock: TreeMap[String, java.lang.Integer],
      manager: Optional[String],
      openDays: EnumSet[DayOfWeek],
      tags: JList[String]
  )

  final case class ShipmentRequest(warehouse: String, items: List[String], priority: Int)
  final case class SdkShipmentRequest(
      warehouse: Optional[String],
      items: JList[String],
      priority: Optional[java.lang.Integer]
  )

  final case class ConnectionSettings(entries: Map[String, String])
  final case class SdkConnectionSettings(entries: Properties)

  final case class AuditBatch(events: Stream[String], sizes: JLinkedHashMap[String, java.lang.Long])
  final case class AuditSummary(events: List[String], sizes: Map[String, Long])

  private def sampleWarehouse(): Warehouse = Warehouse(
    code = "WH-BEL-01",
    bins = List(Bin("A1", 40, fragile = false), Bin("B7", 12, fragile = true)),
    stock = Map("PEN-9" -> 500, "BOOK-1" -> 25, "MUG-3" -> 80),
    manager = Some("Ada Lovelace"),
    openDays = Set(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    tags = Vector("cold-storage", "dock-2")
  )

  @Test
  def exportsScalaWarehouseIntoJavaSdkDto(): Unit = {
    val dto: SdkWarehouse = sampleWarehouse().transformInto[SdkWarehouse]

    assertEquals("WH-BEL-01", dto.code)
    assertEquals(2, dto.bins.size)
    assertEquals(SdkBin("A1", Integer.valueOf(40), java.lang.Boolean.FALSE), dto.bins.get(0))
    assertEquals(SdkBin("B7", Integer.valueOf(12), java.lang.Boolean.TRUE), dto.bins.get(1))

    assertEquals(Arrays.asList("BOOK-1", "MUG-3", "PEN-9"), new JArrayList[String](dto.stock.keySet))
    assertEquals(Integer.valueOf(500), dto.stock.get("PEN-9"))
    assertEquals(Optional.of("Ada Lovelace"), dto.manager)
    assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), dto.openDays)
    assertEquals(Arrays.asList("cold-storage", "dock-2"), dto.tags)

    val unmanaged: SdkWarehouse = sampleWarehouse().copy(manager = None).transformInto[SdkWarehouse]
    assertTrue(unmanaged.manager.isEmpty)
  }

  @Test
  def importsJavaSdkDtoBackIntoScalaWarehouse(): Unit = {
    val bins: JArrayList[SdkBin] = new JArrayList[SdkBin]()
    bins.add(SdkBin("C3", Integer.valueOf(8), java.lang.Boolean.TRUE))
    val stock: TreeMap[String, java.lang.Integer] = new TreeMap[String, java.lang.Integer]()
    stock.put("TAPE-4", Integer.valueOf(120))
    stock.put("BOX-2", Integer.valueOf(64))
    val dto: SdkWarehouse = SdkWarehouse(
      code = "WH-NIS-02",
      bins = bins,
      stock = stock,
      manager = Optional.empty[String](),
      openDays = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
      tags = JList.of("overflow")
    )

    val warehouse: Warehouse = dto.transformInto[Warehouse]
    assertEquals(
      Warehouse(
        code = "WH-NIS-02",
        bins = List(Bin("C3", 8, fragile = true)),
        stock = Map("BOX-2" -> 64, "TAPE-4" -> 120),
        manager = None,
        openDays = Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
        tags = Vector("overflow")
      ),
      warehouse
    )

    val original: Warehouse = sampleWarehouse()
    assertEquals(original, original.transformInto[SdkWarehouse].transformInto[Warehouse])
  }

  @Test
  def decodesSdkRequestPartiallyAndReportsEmptyOptionals(): Unit = {
    val complete: SdkShipmentRequest =
      SdkShipmentRequest(Optional.of("WH-BEL-01"), JList.of("PEN-9", "MUG-3"), Optional.of(Integer.valueOf(2)))
    val decoded: partial.Result[ShipmentRequest] = complete.transformIntoPartial[ShipmentRequest]
    assertEquals(Right(ShipmentRequest("WH-BEL-01", List("PEN-9", "MUG-3"), 2)), decoded.asEither)

    val incomplete: SdkShipmentRequest =
      SdkShipmentRequest(Optional.empty[String](), JList.of("BOOK-1"), Optional.empty[java.lang.Integer]())
    val failed: partial.Result[ShipmentRequest] = incomplete.transformIntoPartial[ShipmentRequest]
    assertTrue(failed.asOption.isEmpty)
    assertEquals(
      List("warehouse" -> "empty value", "priority" -> "empty value"),
      failed.asErrorPathMessageStrings.toList
    )

    val firstOnly: partial.Result[ShipmentRequest] =
      incomplete.transformIntoPartial[ShipmentRequest](failFast = true)
    assertEquals(List("warehouse" -> "empty value"), firstOnly.asErrorPathMessageStrings.toList)
  }

  @Test
  def convertsConnectionSettingsBetweenScalaMapAndProperties(): Unit = {
    val settings: ConnectionSettings =
      ConnectionSettings(Map("host" -> "inventory.internal", "port" -> "8443", "tls" -> "true"))

    val sdk: SdkConnectionSettings = settings.transformInto[SdkConnectionSettings]
    assertEquals("inventory.internal", sdk.entries.getProperty("host"))
    assertEquals("8443", sdk.entries.getProperty("port"))
    assertEquals(3, sdk.entries.size)

    sdk.entries.setProperty("timeout", "30s")
    val reloaded: ConnectionSettings = sdk.transformInto[ConnectionSettings]
    assertEquals(settings.entries + ("timeout" -> "30s"), reloaded.entries)
  }

  @Test
  def drainsJavaStreamAndUnboxesLongsIntoAuditSummary(): Unit = {
    val sizes: JLinkedHashMap[String, java.lang.Long] = new JLinkedHashMap[String, java.lang.Long]()
    sizes.put("received", java.lang.Long.valueOf(4096L))
    sizes.put("shipped", java.lang.Long.valueOf(1024L))
    val batch: AuditBatch = AuditBatch(Stream.of("received:PEN-9", "shipped:MUG-3", "received:BOOK-1"), sizes)

    val summary: AuditSummary = batch.transformInto[AuditSummary]
    assertEquals(List("received:PEN-9", "shipped:MUG-3", "received:BOOK-1"), summary.events)
    assertEquals(Map("received" -> 4096L, "shipped" -> 1024L), summary.sizes)

    val counts: JList[java.lang.Integer] = List(3, 1, 4).transformInto[JList[java.lang.Integer]]
    assertEquals(Arrays.asList(Integer.valueOf(3), Integer.valueOf(1), Integer.valueOf(4)), counts)
    assertEquals(Vector(3, 1, 4), counts.transformInto[Vector[Int]])
  }
}
