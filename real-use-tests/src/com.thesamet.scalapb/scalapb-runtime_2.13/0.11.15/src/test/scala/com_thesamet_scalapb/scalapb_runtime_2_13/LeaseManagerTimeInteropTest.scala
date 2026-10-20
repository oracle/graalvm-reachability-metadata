/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.duration.Duration
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.DurationConverters
import scalapb.TimestampConverters

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.mutable.ListBuffer

/**
 * Exercises the `java.time` interop of the `Timestamp` and `Duration` well-known types:
 * conversion to and from `Instant` and `java.time.Duration`, their `ordering`, and the
 * `isValid` and `checkValid` range checks.
 *
 * A lock service hands out leases on named resources. Every lease is persisted as a `Struct`
 * row whose `granted_at` and `ttl` columns hold the serialized `Timestamp` and `Duration`, so
 * any client can read them. The service computes expiries with `java.time`, sweeps expired
 * leases in expiry order, and rejects renewal requests whose wire values fall outside the
 * ranges protobuf allows.
 */
class LeaseManagerTimeInteropTest {
  private val epoch: Instant = Instant.parse("2026-03-01T08:00:00Z")

  @Test
  def leasesRoundTripThroughProtobufTimeTypesWithoutLosingPrecision(): Unit = {
    val store: LeaseStore = new LeaseStore
    val grantedAt: Instant = epoch.plusNanos(123456789L)
    val ttl: java.time.Duration = java.time.Duration.ofMinutes(5).plusMillis(250)
    store.grant("orders-db", "worker-1", grantedAt, ttl)

    val lease: Lease = store.read("orders-db")
    assertEquals(grantedAt, lease.grantedAt)
    assertEquals(ttl, lease.ttl)
    assertEquals(Instant.parse("2026-03-01T08:05:00.373456789Z"), lease.expiresAt)

    val wireGrant: Timestamp = Timestamp(grantedAt)
    assertEquals(grantedAt.getEpochSecond, wireGrant.seconds)
    assertEquals(123456789, wireGrant.nanos)
    assertEquals(wireGrant, TimestampConverters.fromJavaInstant(grantedAt))
    assertEquals(grantedAt, TimestampConverters.asJavaInstant(wireGrant))

    val wireTtl: Duration = Duration(ttl)
    assertEquals(300L, wireTtl.seconds)
    assertEquals(250000000, wireTtl.nanos)
    assertEquals(wireTtl, DurationConverters.fromJavaDuration(ttl))
    assertEquals(ttl, DurationConverters.asJavaDuration(wireTtl))

    val beforeEpoch: Instant = Instant.parse("1969-12-31T23:59:59.250Z")
    val legacyGrant: Timestamp = Timestamp(beforeEpoch)
    assertEquals(-1L, legacyGrant.seconds)
    assertEquals(250000000, legacyGrant.nanos)
    assertEquals(beforeEpoch, legacyGrant.asJavaInstant)
  }

  @Test
  def sweeperReleasesExpiredLeasesInExpiryOrder(): Unit = {
    val store: LeaseStore = new LeaseStore
    store.grant("inventory", "worker-2", epoch, java.time.Duration.ofSeconds(90))
    store.grant("payments", "worker-3", epoch.plusSeconds(10), java.time.Duration.ofSeconds(30))
    store.grant("search-index", "worker-1", epoch.minusSeconds(5), java.time.Duration.ofSeconds(60))
    store.grant("reports", "worker-4", epoch, java.time.Duration.ofHours(1))
    store.grant("shipping", "worker-2", epoch.plusMillis(1), java.time.Duration.ofSeconds(40))

    val now: Instant = epoch.plus(70, ChronoUnit.SECONDS)
    val released: List[String] = store.sweep(now)

    assertEquals(List("payments", "shipping", "search-index"), released)
    assertEquals(Set("inventory", "reports"), store.resources)

    val ttls: List[Duration] = List(
      Duration(java.time.Duration.ofSeconds(90)),
      Duration(java.time.Duration.ofMillis(500)),
      Duration(java.time.Duration.ofSeconds(-3)),
      Duration(java.time.Duration.ofSeconds(1, 1))
    )
    assertEquals(
      List(
        java.time.Duration.ofSeconds(-3),
        java.time.Duration.ofMillis(500),
        java.time.Duration.ofSeconds(1, 1),
        java.time.Duration.ofSeconds(90)
      ),
      ttls.sorted(Duration.ordering).map(_.asJavaDuration)
    )
    assertEquals(Duration(seconds = 90L), ttls.max(Duration.ordering))
  }

  @Test
  def renewalsWithOutOfRangeWireValuesAreRejected(): Unit = {
    val store: LeaseStore = new LeaseStore
    store.grant("orders-db", "worker-1", epoch, java.time.Duration.ofSeconds(30))

    store.renew("orders-db", Timestamp(epoch.plusSeconds(20)), Duration(seconds = 45L))
    assertEquals(epoch.plusSeconds(65), store.read("orders-db").expiresAt)

    assertTrue(Timestamp.isValid(Timestamp.TIMESTAMP_SECONDS_MAX, 999999999))
    assertFalse(Timestamp.isValid(Timestamp.TIMESTAMP_SECONDS_MAX + 1, 0))
    assertFalse(Timestamp.isValid(Timestamp(seconds = 10L, nanos = -1)))
    assertTrue(Duration.isValid(Duration(seconds = -5L, nanos = -500000000)))
    assertFalse(Duration.isValid(Duration(seconds = 5L, nanos = -1)))
    assertFalse(Duration.isValid(Duration.DURATION_SECONDS_MAX + 1, 0))

    val badNanos: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => store.renew("orders-db", Timestamp(seconds = epoch.getEpochSecond, nanos = 1000000000),
        Duration(seconds = 10L))
    )
    assertTrue(badNanos.getMessage.startsWith("requirement failed: Timestamp Timestamp("), badNanos.getMessage)

    val year10000: Timestamp = Timestamp(seconds = Timestamp.TIMESTAMP_SECONDS_MAX + 1)
    assertThrows(
      classOf[IllegalArgumentException],
      () => store.renew("orders-db", year10000, Duration(seconds = 10L))
    )

    val mixedSigns: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => store.renew("orders-db", Timestamp(epoch), Duration(seconds = 10L, nanos = -1))
    )
    assertTrue(mixedSigns.getMessage.startsWith("requirement failed: Duration Duration("), mixedSigns.getMessage)

    assertEquals(epoch.plusSeconds(65), store.read("orders-db").expiresAt)
  }

  /** A lease as the service reasons about it, in `java.time` terms. */
  final case class Lease(resource: String, holder: String, grantedAt: Instant,
      ttl: java.time.Duration) {
    def expiresAt: Instant = grantedAt.plus(ttl)
  }

  /** Lease rows kept as `Struct` messages whose time columns hold serialized protobuf values. */
  final class LeaseStore {
    private val rows: ListBuffer[Struct] = ListBuffer.empty

    def grant(resource: String, holder: String, grantedAt: Instant,
        ttl: java.time.Duration): Unit =
      write(resource, holder, Timestamp(grantedAt), Duration(ttl))

    def renew(resource: String, grantedAt: Timestamp, ttl: Duration): Unit = {
      val validGrant: Timestamp = Timestamp.checkValid(grantedAt)
      val validTtl: Duration = Duration.checkValid(ttl)
      write(resource, read(resource).holder, validGrant, validTtl)
    }

    def read(resource: String): Lease =
      rows.map(toLease).find(_.resource == resource).getOrElse(
        throw new NoSuchElementException(resource))

    def resources: Set[String] = rows.map(toLease).map(_.resource).toSet

    /** Releases every lease that has expired by `now`, earliest expiry first. */
    def sweep(now: Instant): List[String] = {
      val nowTs: Timestamp = Timestamp(now)
      val expired: List[(Timestamp, String)] = rows.toList.map(toLease)
        .map(lease => (Timestamp(lease.expiresAt), lease.resource))
        .filter { case (expiry, _) => Timestamp.ordering.lteq(expiry, nowTs) }
        .sortBy(_._1)(Timestamp.ordering)
      val released: List[String] = expired.map(_._2)
      rows.filterInPlace(row => !released.contains(toLease(row).resource))
      released
    }

    private def write(resource: String, holder: String, grantedAt: Timestamp,
        ttl: Duration): Unit = {
      rows.filterInPlace(row => toLease(row).resource != resource)
      rows += Struct(fields = Map(
        "resource" -> Value(Value.Kind.StringValue(resource)),
        "holder" -> Value(Value.Kind.StringValue(holder)),
        "granted_at" -> Value(Value.Kind.StringValue(
          java.util.Base64.getEncoder.encodeToString(grantedAt.toByteArray))),
        "ttl" -> Value(Value.Kind.StringValue(
          java.util.Base64.getEncoder.encodeToString(ttl.toByteArray)))
      ))
    }

    private def toLease(row: Struct): Lease = {
      val grantedAt: Timestamp = Timestamp.parseFrom(
        java.util.Base64.getDecoder.decode(row.fields("granted_at").getStringValue))
      val ttl: Duration = Duration.parseFrom(
        java.util.Base64.getDecoder.decode(row.fields("ttl").getStringValue))
      Lease(
        row.fields("resource").getStringValue,
        row.fields("holder").getStringValue,
        grantedAt.asJavaInstant,
        ttl.asJavaDuration
      )
    }
  }
}
