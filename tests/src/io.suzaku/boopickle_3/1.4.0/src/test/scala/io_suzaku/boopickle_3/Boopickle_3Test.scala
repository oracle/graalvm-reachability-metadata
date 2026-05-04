/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_suzaku.boopickle_3

import boopickle.Default.*
import boopickle.{PickleState, UnpickleState}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID
import scala.collection.immutable.SeqMap
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

final case class Address(street: String, postalCode: Int)

final case class CustomerProfile(
    id: UUID,
    name: String,
    primaryAddress: Address,
    aliases: Vector[String],
    metadata: Map[String, Either[Int, String]],
    discount: Option[BigDecimal])

sealed trait InventoryEvent
final case class StockAdded(sku: String, count: Int, source: Option[String]) extends InventoryEvent
final case class StockRemoved(sku: String, count: Int, reason: String) extends InventoryEvent

sealed trait WireCommand
final case class PutValue(key: String, value: Array[Byte]) extends WireCommand
final case class DeleteValue(key: String) extends WireCommand

final case class Money(cents: Long)

final case class SharedNode(label: String, ordinal: Int)

final class DomainFailure(message: String) extends RuntimeException(message)

class Boopickle_3Test {
  @Test
  def roundTripsPrimitiveCollectionAndTupleValues(): Unit = {
    val value: (
        Int,
        Long,
        Boolean,
        Char,
        Float,
        Double,
        String,
        Option[String],
        Either[String, Int],
        List[Int],
        Vector[String],
        Map[String, Int]) = (
      42,
      Long.MaxValue - 7L,
      true,
      'λ',
      12.5f,
      Math.PI,
      "BooPickle ✅",
      Some("present"),
      Right(2048),
      List(1, 1, 2, 3, 5, 8),
      Vector("alpha", "beta", "gamma"),
      Map("one" -> 1, "two" -> 2)
    )

    assertEquals(value, roundTrip(value))
    assertEquals(None, roundTrip(Option.empty[String]))
    assertEquals(Left("problem"), roundTrip(Left[String, Int]("problem")))
  }

  @Test
  def roundTripsSpecialScalarValuesAndDurations(): Unit = {
    val value: (BigInt, BigDecimal, UUID, FiniteDuration, Duration) = (
      BigInt("123456789012345678901234567890"),
      BigDecimal("1234567890.0987654321"),
      UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
      1500.millis,
      Duration.Inf
    )

    assertEquals(value, roundTrip(value))
    assertEquals(Duration.MinusInf, roundTrip(Duration.MinusInf))
    assertSame(Duration.Undefined, roundTrip(Duration.Undefined))
  }

  @Test
  def roundTripsPrimitiveArraysAndByteBuffers(): Unit = {
    val intArray: Array[Int] = Array(3, 1, 4, 1, 5, 9)
    val doubleArray: Array[Double] = Array(2.0d, 2.5d, 3.5d)
    val byteBuffer: ByteBuffer = ByteBuffer.wrap(Array[Byte](10, 20, 30, 40, 50))

    assertArrayEquals(intArray, roundTrip(intArray))
    assertArrayEquals(doubleArray, roundTrip(doubleArray), 0.0d)
    assertArrayEquals(Array[Byte](10, 20, 30, 40, 50), remainingBytes(roundTrip(byteBuffer)))
  }

  @Test
  def derivesPicklersForNestedCaseClasses(): Unit = {
    val profile: CustomerProfile = CustomerProfile(
      UUID.fromString("7ec99e3c-cbb4-4788-9ec7-8f8fdf377b2f"),
      "Ada",
      Address("Compiler Avenue", 1843),
      Vector("Augusta", "Lovelace"),
      Map("score" -> Left(99), "tier" -> Right("gold")),
      Some(BigDecimal("12.50"))
    )

    assertEquals(profile, roundTrip(profile))
  }

  @Test
  def derivesPicklersForSealedTraitHierarchies(): Unit = {
    val events: Vector[InventoryEvent] = Vector(
      StockAdded("book-1", 25, Some("warehouse")),
      StockRemoved("book-1", 3, "shipment"),
      StockAdded("pen-9", 100, None)
    )

    assertEquals(events, roundTrip(events))
  }

  @Test
  def usesCompositePicklerForExplicitPolymorphicProtocols(): Unit = {
    given Pickler[PutValue] = generatePickler[PutValue]
    given Pickler[DeleteValue] = generatePickler[DeleteValue]
    given Pickler[WireCommand] = compositePickler[WireCommand]
      .addConcreteType[PutValue]
      .addConcreteType[DeleteValue]

    val commands: List[WireCommand] = List(
      PutValue("first", Array[Byte](1, 2, 3)),
      DeleteValue("obsolete"),
      PutValue("second", Array[Byte](5, 8, 13))
    )

    val decoded: List[WireCommand] = roundTrip(commands)
    assertEquals(commands.map(_.getClass), decoded.map(_.getClass))
    assertArrayEquals(Array[Byte](1, 2, 3), decoded.head.asInstanceOf[PutValue].value)
    assertEquals(DeleteValue("obsolete"), decoded(1))
    assertArrayEquals(Array[Byte](5, 8, 13), decoded(2).asInstanceOf[PutValue].value)
  }

  @Test
  def usesTransformPicklersForDomainValueObjects(): Unit = {
    given Pickler[Money] = transformPickler((cents: Long) => Money(cents))(_.cents)

    val prices: Seq[Money] = Seq(Money(199), Money(2500), Money(123456789L))

    assertEquals(prices, roundTrip(prices))
  }

  @Test
  def preservesSharedObjectReferencesWhenDeduplicationIsEnabled(): Unit = {
    val shared: SharedNode = SharedNode("same instance", 1)
    val decoded: (SharedNode, SharedNode) = roundTrip((shared, shared))

    assertEquals(shared, decoded._1)
    assertSame(decoded._1, decoded._2)
  }

  @Test
  def supportsManualPickleAndUnpickleStatePipelines(): Unit = {
    val pickleState: PickleState = Pickle(List("header", "payload"))
      .pickle(Map("answer" -> 42, "size" -> 2))
    val unpickleState: UnpickleState = UnpickleState(pickleState.toByteBuffer)

    assertEquals(List("header", "payload"), unpickleState.unpickle[List[String]])
    assertEquals(Map("answer" -> 42, "size" -> 2), unpickleState.unpickle[Map[String, Int]])
  }

  @Test
  def writesLargePayloadsAsMultipleByteBufferChunks(): Unit = {
    val value: Vector[Int] = Vector.tabulate(2000)(index => index * 3)
    val chunks: Vector[ByteBuffer] = Pickle.intoByteBuffers(value).toVector

    assertTrue(chunks.size > 1, "Expected the payload to be split across multiple buffers")

    val totalSize: Int = chunks.map(_.remaining()).sum
    val combined: ByteBuffer = ByteBuffer.allocate(totalSize)
    chunks.foreach { chunk =>
      combined.put(chunk.duplicate())
    }
    combined.flip()

    assertEquals(value, Unpickle[Vector[Int]].fromBytes(combined))
  }

  @Test
  def restoresInputByteOrderAndReportsTryFromBytesFailures(): Unit = {
    val bytes: ByteBuffer = Pickle.intoBytes(Vector("little", "endian"))
    bytes.order(ByteOrder.BIG_ENDIAN)

    val decoded: Vector[String] = Unpickle[Vector[String]].fromBytes(bytes)

    assertEquals(Vector("little", "endian"), decoded)
    assertEquals(ByteOrder.BIG_ENDIAN, bytes.order())

    Unpickle[Vector[String]].tryFromBytes(ByteBuffer.wrap(Array[Byte](1))) match {
      case Failure(_: IllegalArgumentException) => ()
      case Failure(_: RuntimeException)         => ()
      case Failure(unexpected)                  => fail(s"Unexpected failure type: ${unexpected.getClass.getName}")
      case Success(value)                       => fail(s"Expected decoding to fail, but decoded $value")
    }
  }

  @Test
  def picklesBuiltInAndCustomExceptionMessages(): Unit = {
    val customExceptionPickler: Pickler[Throwable] = exceptionPickler
      .addException[DomainFailure](message => new DomainFailure(message))

    val builtIn: Throwable = roundTrip[Throwable](new IllegalArgumentException("bad argument"))(using customExceptionPickler)
    val custom: Throwable = roundTrip[Throwable](new DomainFailure("domain rejected"))(using customExceptionPickler)

    assertTrue(builtIn.isInstanceOf[IllegalArgumentException])
    assertEquals("bad argument", builtIn.getMessage)
    assertTrue(custom.isInstanceOf[DomainFailure])
    assertEquals("domain rejected", custom.getMessage)
  }

  @Test
  def roundTripsSeqMapWhilePreservingIterationOrder(): Unit = {
    val routingTable: SeqMap[String, Int] = SeqMap(
      "first" -> 10,
      "second" -> 20,
      "third" -> 30
    )

    val decoded: SeqMap[String, Int] = roundTrip(routingTable)

    assertEquals(routingTable, decoded)
    assertEquals(routingTable.keys.toVector, decoded.keys.toVector)
  }

  private def roundTrip[A](value: A)(using pickler: Pickler[A]): A = {
    val bytes: ByteBuffer = Pickle.intoBytes(value)(using PickleState.pickleStateSpeed, pickler)
    Unpickle[A].fromBytes(bytes)(using UnpickleState.unpickleStateSpeed)
  }

  private def remainingBytes(buffer: ByteBuffer): Array[Byte] = {
    val duplicate: ByteBuffer = buffer.slice()
    val bytes: Array[Byte] = new Array[Byte](duplicate.remaining())
    duplicate.get(bytes)
    bytes
  }
}
