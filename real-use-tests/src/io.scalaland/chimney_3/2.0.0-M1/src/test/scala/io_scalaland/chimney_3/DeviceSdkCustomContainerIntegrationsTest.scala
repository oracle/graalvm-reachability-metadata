/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.integrations.DefaultValue
import io.scalaland.chimney.integrations.OptionalValue
import io.scalaland.chimney.integrations.PartiallyBuildIterable
import io.scalaland.chimney.integrations.TotalOuterTransformer
import io.scalaland.chimney.integrations.TotallyBuildIterable
import io.scalaland.chimney.integrations.TotallyBuildMap
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.collection.Factory
import scala.collection.immutable.ListMap
import scala.collection.mutable

/**
 * The `integrations` extension points that teach Chimney an application's own container types.
 *
 * A telemetry ingest service receives uploads from a device SDK that ships its own optional,
 * history and envelope types, and stores them in a domain model built on its own non-empty
 * batch and ordered registry. The service registers `OptionalValue`, `TotallyBuildIterable`,
 * `PartiallyBuildIterable`, `TotallyBuildMap`, `TotalOuterTransformer` and `DefaultValue`
 * instances as `given`s, after which derived transformers convert between those types and the
 * standard library ones, unwrap and validate them in partial transformations, and fill fields the
 * SDK never sends.
 */
class DeviceSdkCustomContainerIntegrationsTest {

  /** The SDK's optional value: absent values are carried as `null` on the wire. */
  final class Maybe[A](private val raw: A | Null) {
    def toOption: Option[A] = Option(raw).map(_.asInstanceOf[A])
    override def equals(other: Any): Boolean = other match {
      case that: Maybe[?] => toOption == that.toOption
      case _ => false
    }
    override def hashCode: Int = toOption.hashCode
    override def toString: String = toOption.fold("Maybe.absent")(value => s"Maybe($value)")
  }

  /** The SDK's append-only history of readings. */
  final class Ledger[A](val entries: List[A]) {
    override def equals(other: Any): Boolean = other match {
      case that: Ledger[?] => entries == that.entries
      case _ => false
    }
    override def hashCode: Int = entries.hashCode
    override def toString: String = entries.mkString("Ledger(", ", ", ")")
  }

  /** The SDK's message envelope that tags a payload with a correlation id. */
  final class Envelope[A](val correlationId: String, val payload: A) {
    override def equals(other: Any): Boolean = other match {
      case that: Envelope[?] => correlationId == that.correlationId && payload == that.payload
      case _ => false
    }
    override def hashCode: Int = (correlationId, payload).hashCode
    override def toString: String = s"Envelope($correlationId, $payload)"
  }

  /** The domain's batch of samples, which must never be empty. */
  final class Batch[A](val items: Vector[A]) {
    override def equals(other: Any): Boolean = other match {
      case that: Batch[?] => items == that.items
      case _ => false
    }
    override def hashCode: Int = items.hashCode
    override def toString: String = items.mkString("Batch(", ", ", ")")
  }

  /** The domain's label registry, which keeps labels in the order the device sent them. */
  final class Registry[K, V](val entries: ListMap[K, V]) {
    override def equals(other: Any): Boolean = other match {
      case that: Registry[?, ?] => entries.toList == that.entries.toList
      case _ => false
    }
    override def hashCode: Int = entries.toList.hashCode
    override def toString: String = entries.mkString("Registry(", ", ", ")")
  }

  final case class Region(code: String)

  final case class RawSample(sensor: String, celsius: Double, note: Maybe[String])
  final case class RawUpload(
      device: String,
      firmware: Maybe[String],
      samples: List[RawSample],
      labels: Map[String, String],
      history: Ledger[Int]
  )

  final case class Sample(sensor: String, celsius: Double, note: Option[String])
  final case class Upload(
      device: String,
      firmware: Option[String],
      samples: Batch[Sample],
      labels: Registry[String, String],
      history: Vector[Int],
      region: Region
  )

  final case class StrictSample(sensor: String, celsius: Double, note: String)
  final case class ArchivedUpload(device: String, firmware: Maybe[String], samples: Ledger[Sample])

  given [A]: OptionalValue[Maybe[A], A] with {
    def empty: Maybe[A] = Maybe[A](null)
    def of(value: A): Maybe[A] = Maybe[A](value)
    def fold[B](maybe: Maybe[A], onNone: => B, onSome: A => B): B = maybe.toOption.fold(onNone)(onSome)
  }

  given [A]: TotallyBuildIterable[Ledger[A], A] with {
    def totalFactory: Factory[A, Ledger[A]] = new Factory[A, Ledger[A]] {
      def fromSpecific(it: IterableOnce[A]): Ledger[A] = Ledger(List.from(it))
      def newBuilder: mutable.Builder[A, Ledger[A]] = List.newBuilder[A].mapResult(Ledger(_))
    }
    def iterator(ledger: Ledger[A]): Iterator[A] = ledger.entries.iterator
  }

  given [A]: PartiallyBuildIterable[Batch[A], A] with {
    def partialFactory: Factory[A, partial.Result[Batch[A]]] = new Factory[A, partial.Result[Batch[A]]] {
      def fromSpecific(it: IterableOnce[A]): partial.Result[Batch[A]] = newBuilder.addAll(it).result()
      def newBuilder: mutable.Builder[A, partial.Result[Batch[A]]] = Vector.newBuilder[A].mapResult { items =>
        if (items.isEmpty) partial.Result.fromErrorString("a batch needs at least one sample")
        else partial.Result.fromValue(Batch(items))
      }
    }
    def iterator(batch: Batch[A]): Iterator[A] = batch.items.iterator
  }

  given [K, V]: TotallyBuildMap[Registry[K, V], K, V] with {
    def totalFactory: Factory[(K, V), Registry[K, V]] = new Factory[(K, V), Registry[K, V]] {
      def fromSpecific(it: IterableOnce[(K, V)]): Registry[K, V] = Registry(ListMap.from(it))
      def newBuilder: mutable.Builder[(K, V), Registry[K, V]] = ListMap.newBuilder[K, V].mapResult(Registry(_))
    }
    def iterator(registry: Registry[K, V]): Iterator[(K, V)] = registry.entries.iterator
  }

  given [A, B]: TotalOuterTransformer[Envelope[A], Envelope[B], A, B] with {
    def transformWithTotalInner(src: Envelope[A], inner: A => B): Envelope[B] =
      Envelope(src.correlationId, inner(src.payload))
    def transformWithPartialInner(
        src: Envelope[A],
        failFast: Boolean,
        inner: A => partial.Result[B]
    ): partial.Result[Envelope[B]] = inner(src.payload).map(Envelope(src.correlationId, _))
  }

  /** Uploads that predate region tagging belong to the default region. */
  given DefaultValue[Region] = () => Region("eu-west")

  private val rawUpload: RawUpload = RawUpload(
    device = "thermo-17",
    firmware = Maybe("4.2.1"),
    samples = List(
      RawSample("boiler", 71.5, Maybe("after restart")),
      RawSample("return", 48.0, Maybe(null))
    ),
    labels = ListMap("site" -> "plant-3", "floor" -> "2", "owner" -> "ops"),
    history = Ledger(List(3, 5, 8))
  )

  private val expectedUpload: Upload = Upload(
    device = "thermo-17",
    firmware = Some("4.2.1"),
    samples = Batch(
      Vector(Sample("boiler", 71.5, Some("after restart")), Sample("return", 48.0, None))
    ),
    labels = Registry(ListMap("site" -> "plant-3", "floor" -> "2", "owner" -> "ops")),
    history = Vector(3, 5, 8),
    region = Region("eu-west")
  )

  @Test
  def ingestsAnEnvelopedUploadThroughEveryRegisteredIntegration(): Unit = {
    val envelope: Envelope[RawUpload] = Envelope("corr-81", rawUpload)

    val result: partial.Result[Envelope[Upload]] = envelope
      .intoPartial[Envelope[Upload]]
      .enableDefaultValueOfType[Region]
      .transform

    assertEquals(Right(Envelope("corr-81", expectedUpload)), result.asEither)
  }

  @Test
  def rejectsAnUploadWithoutSamplesThroughTheBatchFactory(): Unit = {
    val empty: RawUpload = RawUpload("thermo-18", Maybe(null), Nil, Map.empty, Ledger(Nil))

    val result: partial.Result[Upload] = empty
      .intoPartial[Upload]
      .enableDefaultValueOfType[Region]
      .transform

    assertEquals(
      List("samples" -> "a batch needs at least one sample"),
      result.asErrorPathMessageStrings.toList
    )
  }

  @Test
  def unwrapsMissingSdkOptionalsAsFieldErrors(): Unit = {
    val samples: List[RawSample] = List(
      RawSample("boiler", 71.5, Maybe("ok")),
      RawSample("return", 48.0, Maybe(null)),
      RawSample("flue", 180.0, Maybe(null))
    )

    val result: partial.Result[Vector[StrictSample]] = samples.transformIntoPartial[Vector[StrictSample]]

    assertEquals(
      List("(1).note" -> "empty value", "(2).note" -> "empty value"),
      result.asErrorPathMessageStrings.toList
    )

    val strict: partial.Result[StrictSample] = samples.head.transformIntoPartial[StrictSample]
    assertEquals(Some(StrictSample("boiler", 71.5, "ok")), strict.asOption)
  }

  @Test
  def archivesDomainUploadsBackIntoSdkContainers(): Unit = {
    val archived: ArchivedUpload = expectedUpload.transformInto[ArchivedUpload]

    assertEquals(
      ArchivedUpload(
        device = "thermo-17",
        firmware = Maybe("4.2.1"),
        samples = Ledger(List(Sample("boiler", 71.5, Some("after restart")), Sample("return", 48.0, None)))
      ),
      archived
    )

    val wrapped: Envelope[Maybe[String]] = Envelope("corr-82", Option("5.0.0")).transformInto[Envelope[Maybe[String]]]
    assertEquals(Envelope("corr-82", Maybe("5.0.0")), wrapped)

    val labels: Map[String, String] = expectedUpload.labels.transformInto[Map[String, String]]
    assertEquals(Map("site" -> "plant-3", "floor" -> "2", "owner" -> "ops"), labels)
  }
}
