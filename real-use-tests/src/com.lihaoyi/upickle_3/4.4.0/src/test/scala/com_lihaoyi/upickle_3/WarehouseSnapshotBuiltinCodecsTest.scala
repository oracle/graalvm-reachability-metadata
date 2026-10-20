/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.util.UUID

import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.default.*

/**
 * Built-in readers and writers for Scala and Java standard-library types (primitives at their
 * numeric limits, Option, Either, tuples, maps with primitive and structured keys, sorted
 * collections, arrays, UUID, BigInt and Duration), as a warehouse service persists an
 * inventory snapshot without declaring a single custom codec.
 */
class WarehouseSnapshotBuiltinCodecsTest {

  import WarehouseSnapshotBuiltinCodecsTest.*

  @Test
  def primitivesAtTheirLimitsKeepTheirValues(): Unit = {
    assertEquals("\"9223372036854775807\"", write(Long.MaxValue))
    assertEquals("4096", write(4096L))
    assertEquals(Long.MaxValue, read[Long](write(Long.MaxValue)))
    assertEquals(42L, read[Long]("42"))
    assertEquals(42L, read[Long]("\"42\""))
    assertEquals("\"NaN\"", write(Double.NaN))
    assertTrue(read[Double]("\"NaN\"").isNaN)
    assertEquals("\"Infinity\"", write(Double.PositiveInfinity))
    assertEquals(Double.NegativeInfinity, read[Double]("\"-Infinity\""))
    assertEquals("\"Z\"", write('Z'))
    assertEquals('Z', read[Char]("\"Z\""))
    val huge: BigInt = BigInt("123456789012345678901234567890")
    assertEquals("\"123456789012345678901234567890\"", write(huge))
    assertEquals(huge, read[BigInt](write(huge)))
  }

  @Test
  def optionEitherAndTuplesUseTheirStandardShapes(): Unit = {
    assertEquals("null", write[Option[Int]](None))
    assertEquals("7", write[Option[Int]](Some(7)))
    assertEquals(Some(7), read[Option[Int]]("7"))
    assertEquals(None, read[Option[Int]]("null"))

    val accepted: Either[String, Int] = Right(12)
    val rejected: Either[String, Int] = Left("bin full")
    assertEquals("[1,12]", write(accepted))
    assertEquals("[0,\"bin full\"]", write(rejected))
    assertEquals(accepted, read[Either[String, Int]]("[1,12]"))
    assertEquals(rejected, read[Either[String, Int]]("[0,\"bin full\"]"))

    val location: (String, Int, Int) = ("A", 4, 17)
    assertEquals("[\"A\",4,17]", write(location))
    assertEquals(location, read[(String, Int, Int)]("[\"A\",4,17]"))
  }

  @Test
  def mapsUseObjectsForPrimitiveKeysAndPairsForStructuredKeys(): Unit = {
    val stockByBin: Map[Int, Int] = Map(3 -> 40, 11 -> 0)
    val stockJson: ujson.Value = writeJs(stockByBin)
    assertEquals(ujson.Obj("3" -> 40, "11" -> 0), stockJson)
    assertEquals(stockByBin, read[Map[Int, Int]](stockJson))

    val bySku: Map[Sku, Int] = Map(Sku("TOOLS", 7) -> 15)
    val bySkuJson: ujson.Value = writeJs(bySku)
    assertEquals(ujson.Arr(ujson.Arr(ujson.Obj("family" -> "TOOLS", "number" -> 7), 15)), bySkuJson)
    assertEquals(bySku, read[Map[Sku, Int]](bySkuJson))

    val sortedZones: SortedMap[String, Int] = SortedMap("north" -> 2, "east" -> 5, "south" -> 1)
    assertEquals("{\"east\":5,\"north\":2,\"south\":1}", write(sortedZones))
    val restored: SortedMap[String, Int] = read[SortedMap[String, Int]]("{\"south\":1,\"north\":2,\"east\":5}")
    assertEquals(List("east", "north", "south"), restored.keys.toList)

    val carriers: SortedSet[String] = read[SortedSet[String]]("[\"ups\",\"dhl\",\"fedex\"]")
    assertEquals(List("dhl", "fedex", "ups"), carriers.toList)
  }

  @Test
  def snapshotOfStandardTypesRoundTrips(): Unit = {
    val snapshot: Snapshot = Snapshot(
      id = UUID.fromString("3f2b8c1e-9a4d-4e6f-8b21-0c5d7e9f1a23"),
      bins = Map(3 -> Vector("SKU-1", "SKU-2"), 11 -> Vector.empty),
      reservations = Map(Sku("TOOLS", 7) -> Right(15), Sku("PAINT", 2) -> Left("recalled")),
      dock = ("D", 2),
      checksum = Array[Byte](1, -2, 127),
      syncTimeout = 90.seconds,
      retention = Duration.Inf,
      totalUnits = 9007199254740993L,
      supervisor = None
    )

    val json: String = write(snapshot)
    val tree: ujson.Value = ujson.read(json)
    assertEquals("3f2b8c1e-9a4d-4e6f-8b21-0c5d7e9f1a23", tree("id").str)
    assertEquals(ujson.Arr(1, -2, 127), tree("checksum"))
    assertEquals(ujson.Str("9007199254740993"), tree("totalUnits"))
    assertEquals(ujson.Null, tree("supervisor"))

    val restored: Snapshot = read[Snapshot](json)
    assertEquals(snapshot.id, restored.id)
    assertEquals(snapshot.bins, restored.bins)
    assertEquals(snapshot.reservations, restored.reservations)
    assertEquals(snapshot.dock, restored.dock)
    assertArrayEquals(snapshot.checksum, restored.checksum)
    assertEquals(snapshot.syncTimeout, restored.syncTimeout)
    assertEquals(Duration.Inf, restored.retention)
    assertEquals(9007199254740993L, restored.totalUnits)
    assertEquals(None, restored.supervisor)

    val binary: Snapshot = readBinary[Snapshot](writeBinary(snapshot))
    assertEquals(snapshot.reservations, binary.reservations)
    assertArrayEquals(snapshot.checksum, binary.checksum)
    assertEquals(snapshot.syncTimeout, binary.syncTimeout)
  }
}

object WarehouseSnapshotBuiltinCodecsTest {

  final case class Sku(family: String, number: Int) derives ReadWriter

  final case class Snapshot(
      id: UUID,
      bins: Map[Int, Vector[String]],
      reservations: Map[Sku, Either[String, Int]],
      dock: (String, Int),
      checksum: Array[Byte],
      syncTimeout: FiniteDuration,
      retention: Duration,
      totalUnits: Long,
      supervisor: Option[String]
  ) derives ReadWriter
}
