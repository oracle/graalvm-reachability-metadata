/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.Abort
import upickle.core.AbortException
import upickle.default.*

/**
 * Derived codecs for wide case classes with more than 64 fields, whose readers track present
 * and missing keys across several bitset words, as a metrics collector ingests host samples
 * from agents of several releases that add, drop and reorder columns.
 */
class HostMetricsWideRecordTest {

  import HostMetricsWideRecordTest.*

  @Test
  def fullSampleRoundTripsWithEveryColumnInDeclarationOrder(): Unit = {
    val sample: HostMetricsSample = sampleFrom("db-17", "fra1").copy(
      rack = "r12",
      maintenanceMode = true,
      labels = Map("role" -> "postgres", "tier" -> "gold"),
      annotations = Seq("kernel upgrade pending"),
      sampleIntervalSeconds = 30
    )

    val json: String = write(sample)
    val record: ujson.Value = ujson.read(json)

    assertEquals(FieldCount, record.obj.size, json)
    assertEquals("hostname", record.obj.keys.head)
    assertEquals("sampleIntervalSeconds", record.obj.keys.last)
    assertEquals("agentVersion", record.obj.keys.toSeq(64))
    assertEquals("fra1", record("datacenter").str)
    assertEquals("r12", record("rack").str)
    assertEquals(true, record("maintenanceMode").bool)
    assertEquals("gold", record("labels")("tier").str)
    assertEquals(sample, read[HostMetricsSample](json))
  }

  @Test
  def defaultValuedTrailingColumnsAreOmittedAndRestored(): Unit = {
    val sample: HostMetricsSample = sampleFrom("web-03", "ams2")

    val record: ujson.Value = writeJs(sample)

    assertEquals(FieldCount - 5, record.obj.size)
    Seq("rack", "maintenanceMode", "labels", "annotations", "sampleIntervalSeconds").foreach { name =>
      assertFalse(record.obj.contains(name), name)
    }
    assertTrue(record.obj.contains("datacenter"))

    val restored: HostMetricsSample = read[HostMetricsSample](record)
    assertEquals("unassigned", restored.rack)
    assertEquals(15, restored.sampleIntervalSeconds)
    assertEquals(sample, restored)
  }

  @Test
  def reorderedSampleFromNewerAgentIgnoresUnknownColumns(): Unit = {
    val expected: HostMetricsSample = sampleFrom("cache-09", "sfo3").copy(rack = "r02")
    val record: ujson.Value = writeJs(expected)
    val reordered: ujson.Obj = ujson.Obj.from(record.obj.toSeq.reverse)
    reordered("gpuMemoryBytes") = 8589934592L
    reordered("cgroupVersion") = 2
    reordered("pressureStallCpuPct") = ujson.Arr(0.1, 0.2, 0.4)

    val restored: HostMetricsSample = read[HostMetricsSample](reordered.render())

    assertEquals(expected, restored)
    assertEquals("r02", restored.rack)
  }

  @Test
  def batchFromMixedAgentReleasesAggregatesAcrossTheFleet(): Unit = {
    val legacy: ujson.Value = writeJs(sampleFrom("api-01", "fra1"))
    legacy.obj.remove("sampleIntervalSeconds")
    val current: ujson.Value = writeJs(sampleFrom("api-02", "fra1").copy(sampleIntervalSeconds = 60))
    val batch: String = ujson.Arr(legacy, current, writeJs(sampleFrom("api-03", "ams2"))).render(indent = 2)

    val samples: Seq[HostMetricsSample] = read[Seq[HostMetricsSample]](batch)

    assertEquals(Seq("api-01", "api-02", "api-03"), samples.map(_.hostname))
    assertEquals(Seq(15, 60, 15), samples.map(_.sampleIntervalSeconds))
    assertEquals(Map("fra1" -> 2, "ams2" -> 1), samples.groupMapReduce(_.datacenter)(_ => 1)(_ + _))
    assertEquals(samples, read[Seq[HostMetricsSample]](write(samples)))
  }

  @Test
  def missingRequiredColumnsOnBothSidesOfTheFirstWordAreReported(): Unit = {
    val record: ujson.Value = writeJs(sampleFrom("batch-11", "sfo3"))
    record.obj.remove("cpuIdlePct")
    record.obj.remove("datacenter")

    val failure: Throwable = assertThrows(classOf[Exception], () => read[HostMetricsSample](record.render()))
    val message: String = abortMessage(failure)

    assertTrue(message.contains("cpuIdlePct"), message)
    assertTrue(message.contains("datacenter"), message)
    assertFalse(message.contains("rack"), message)
    assertFalse(message.contains("hostname"), message)
  }

  @Test
  def missingColumnBeyondTheFirstWordAloneIsReported(): Unit = {
    val record: ujson.Value = writeJs(sampleFrom("batch-12", "sfo3"))
    record.obj.remove("agentVersion")

    val failure: Throwable = assertThrows(classOf[Exception], () => read[HostMetricsSample](record))
    val message: String = abortMessage(failure)

    assertTrue(message.contains("agentVersion"), message)
    assertFalse(message.contains("datacenter"), message)
  }

  /**
   * The message of the abort a failed read carries, possibly wrapped by a trace: an
   * AbortException with a position when parsing text, a bare Abort when reading a ujson.Value.
   */
  private def abortMessage(failure: Throwable): String =
    Iterator.iterate(failure)(_.getCause).takeWhile(_ != null).collectFirst {
      case abort: AbortException => abort.getMessage
      case abort: Abort => abort.getMessage
    }.getOrElse(throw new AssertionError("no abort in " + failure, failure))
}

object HostMetricsWideRecordTest {

  /** Number of columns of a host metrics sample, more than one 64-bit key bitset can track. */
  val FieldCount: Int = 71

  /** One scrape of a host, as the monitoring agent reports it. */
  final case class HostMetricsSample(
      hostname: String,
      collectedAtEpochMs: Long,
      cpuUserPct: Double,
      cpuSystemPct: Double,
      cpuIdlePct: Double,
      cpuIowaitPct: Double,
      cpuStealPct: Double,
      cpuNicePct: Double,
      cpuIrqPct: Double,
      cpuSoftirqPct: Double,
      load1: Double,
      load5: Double,
      load15: Double,
      memTotalBytes: Long,
      memFreeBytes: Long,
      memAvailableBytes: Long,
      memCachedBytes: Long,
      memBuffersBytes: Long,
      memDirtyBytes: Long,
      swapTotalBytes: Long,
      swapFreeBytes: Long,
      diskReadBytes: Long,
      diskWrittenBytes: Long,
      diskReadOps: Long,
      diskWriteOps: Long,
      diskIoTimeMs: Long,
      diskQueueDepth: Int,
      fsRootUsedBytes: Long,
      fsRootFreeBytes: Long,
      netRxBytes: Long,
      netTxBytes: Long,
      netRxPackets: Long,
      netTxPackets: Long,
      netRxErrors: Long,
      netTxErrors: Long,
      netRxDropped: Long,
      netTxDropped: Long,
      tcpEstablished: Int,
      tcpTimeWait: Int,
      procsRunning: Int,
      procsBlocked: Int,
      procsTotal: Int,
      threadsTotal: Int,
      forksTotal: Long,
      contextSwitches: Long,
      interrupts: Long,
      openFileDescriptors: Int,
      maxFileDescriptors: Int,
      entropyAvailable: Int,
      cpuTempCelsius: Double,
      fanRpm: Int,
      powerWatts: Double,
      uptimeSeconds: Long,
      bootTimeEpochSeconds: Long,
      cpuCores: Int,
      cpuMhz: Double,
      numaNodes: Int,
      gpuCount: Int,
      gpuUtilPct: Double,
      ntpOffsetMs: Double,
      ntpSynced: Boolean,
      timezone: String,
      kernelVersion: String,
      osRelease: String,
      agentVersion: String,
      datacenter: String,
      rack: String = "unassigned",
      maintenanceMode: Boolean = false,
      labels: Map[String, String] = Map.empty,
      annotations: Seq[String] = Nil,
      sampleIntervalSeconds: Int = 15
  ) derives ReadWriter

  /** A sample whose metrics derive from the host name, leaving the trailing columns at defaults. */
  def sampleFrom(hostname: String, datacenter: String): HostMetricsSample = {
    val seed: Long = hostname.hashCode.toLong.abs % 1000
    HostMetricsSample(
      hostname = hostname,
      collectedAtEpochMs = 1760000000000L + seed,
      cpuUserPct = 21.5,
      cpuSystemPct = 6.25,
      cpuIdlePct = 70.0,
      cpuIowaitPct = 1.5,
      cpuStealPct = 0.25,
      cpuNicePct = 0.0,
      cpuIrqPct = 0.25,
      cpuSoftirqPct = 0.25,
      load1 = 1.5,
      load5 = 1.25,
      load15 = 0.75,
      memTotalBytes = 68719476736L,
      memFreeBytes = 4294967296L + seed,
      memAvailableBytes = 34359738368L,
      memCachedBytes = 25769803776L,
      memBuffersBytes = 1073741824L,
      memDirtyBytes = 2097152L,
      swapTotalBytes = 8589934592L,
      swapFreeBytes = 8589934592L,
      diskReadBytes = 987654321012L,
      diskWrittenBytes = 123456789012L,
      diskReadOps = 4500000L,
      diskWriteOps = 3200000L,
      diskIoTimeMs = 912345L,
      diskQueueDepth = 2,
      fsRootUsedBytes = 107374182400L,
      fsRootFreeBytes = 429496729600L,
      netRxBytes = 5497558138880L,
      netTxBytes = 2748779069440L,
      netRxPackets = 9000000000L,
      netTxPackets = 7000000000L,
      netRxErrors = 3L,
      netTxErrors = 0L,
      netRxDropped = 12L,
      netTxDropped = 1L,
      tcpEstablished = 412,
      tcpTimeWait = 37,
      procsRunning = 4,
      procsBlocked = 0,
      procsTotal = 318,
      threadsTotal = 1650,
      forksTotal = 9123456L,
      contextSwitches = 81234567890L,
      interrupts = 61234567890L,
      openFileDescriptors = 4096,
      maxFileDescriptors = 1048576,
      entropyAvailable = 256,
      cpuTempCelsius = 54.5,
      fanRpm = 3200,
      powerWatts = 187.5,
      uptimeSeconds = 3888000L + seed,
      bootTimeEpochSeconds = 1756112000L,
      cpuCores = 32,
      cpuMhz = 2450.0,
      numaNodes = 2,
      gpuCount = 0,
      gpuUtilPct = 0.0,
      ntpOffsetMs = -0.125,
      ntpSynced = true,
      timezone = "UTC",
      kernelVersion = "6.8.0-45-generic",
      osRelease = "Ubuntu 24.04.1 LTS",
      agentVersion = "2.14.0",
      datacenter = datacenter
    )
  }
}
