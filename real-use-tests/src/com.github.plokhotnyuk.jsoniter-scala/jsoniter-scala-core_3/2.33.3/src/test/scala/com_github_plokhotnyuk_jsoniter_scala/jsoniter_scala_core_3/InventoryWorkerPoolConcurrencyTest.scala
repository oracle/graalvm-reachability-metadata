/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.readFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToStream
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import InventoryWorkerPoolConcurrencyTest.*

/**
 * Concurrent use of shared codecs through the per-thread pooled `JsonReader` and `JsonWriter`
 * instances behind the top-level read and write functions: an inventory service decodes and
 * encodes warehouse snapshots from many platform and virtual worker threads at once, reuses
 * the same worker thread for a huge document followed by small ones, and keeps serving after a
 * worker hits a malformed payload.
 */
class InventoryWorkerPoolConcurrencyTest {

  @Test
  def platformWorkersRoundTripDistinctSnapshotsInParallel(): Unit = {
    val pool: ExecutorService = Executors.newFixedThreadPool(8)
    try {
      val results: Seq[(Snapshot, Snapshot, String)] = runAll(pool, 64) { index =>
        val snapshot: Snapshot = snapshotFor(index)
        val viaString: Snapshot = readFromString[Snapshot](writeToString(snapshot))
        val viaArray: Snapshot = readFromArray[Snapshot](writeToArray(snapshot))
        (snapshot, viaArray, writeToString(viaString))
      }

      results.zipWithIndex.foreach { case ((expected, viaArray, text), index) =>
        assertEquals(expected, viaArray)
        assertEquals(expected, readFromString[Snapshot](text))
        assertTrue(text.startsWith(s"""{"warehouse":"wh-$index","revision":${index * 7L}"""), text)
      }
      assertEquals(64, results.map(_._1.warehouse).distinct.size)
    } finally shutdown(pool)
  }

  @Test
  def virtualThreadWorkersStreamSnapshotsInParallel(): Unit = {
    val pool: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    try {
      val results: Seq[(Snapshot, Array[Byte])] = runAll(pool, 200) { index =>
        val snapshot: Snapshot = snapshotFor(index)
        val out: ByteArrayOutputStream = new ByteArrayOutputStream()
        writeToStream(snapshot, out)
        val bytes: Array[Byte] = out.toByteArray
        (readFromStream[Snapshot](new ByteArrayInputStream(bytes)), bytes)
      }

      results.zipWithIndex.foreach { case ((decoded, bytes), index) =>
        assertEquals(snapshotFor(index), decoded)
        assertEquals(writeToString(snapshotFor(index)), new String(bytes, "UTF-8"))
      }
    } finally shutdown(pool)
  }

  @Test
  def reusedWorkerDecodesSmallSnapshotsAfterAHugeOne(): Unit = {
    val worker: ExecutorService = Executors.newSingleThreadExecutor()
    try {
      val huge: Snapshot = Snapshot(
        "wh-central",
        999L,
        (1 to 20000).map(i => Stock(f"SKU-$i%06d", i % 500, Seq.fill(i % 3)(s"bin-${i % 97}"))).toList
      )
      val small: Snapshot = Snapshot("wh-tiny", 1L, List(Stock("SKU-1", 1, Seq("bin-1"))))
      val empty: Snapshot = Snapshot("wh-empty", 2L, Nil)

      val decoded: Seq[Snapshot] = submit(worker) { () =>
        val hugeBytes: Array[Byte] = writeToArray(huge)
        val hugeText: String = writeToString(huge)
        Seq(
          readFromArray[Snapshot](hugeBytes),
          readFromString[Snapshot](writeToString(small)),
          readFromArray[Snapshot](writeToArray(empty)),
          readFromString[Snapshot](hugeText),
          readFromArray[Snapshot](writeToArray(small))
        )
      }

      assertEquals(Seq(huge, small, empty, huge, small), decoded)
      val smallText: String = submit(worker)(() => writeToString(small))
      assertEquals("""{"warehouse":"wh-tiny","revision":1,"stock":[{"sku":"SKU-1","quantity":1,"bins":["bin-1"]}]}""",
        smallText)
    } finally shutdown(worker)
  }

  @Test
  def workerKeepsServingAfterMalformedPayloads(): Unit = {
    val pool: ExecutorService = Executors.newFixedThreadPool(4)
    try {
      val outcomes: Seq[Either[String, Snapshot]] = runAll(pool, 40) { index =>
        val payload: String =
          if (index % 4 == 0) s"""{"warehouse":"wh-$index","revision":"not-a-number","stock":[]}"""
          else if (index % 4 == 1) s"""{"warehouse":"wh-$index","revision":$index,"stock":[{"sku":"S"""
          else writeToString(snapshotFor(index))
        try Right(readFromString[Snapshot](payload))
        catch case e: JsonReaderException => Left(e.getMessage)
      }

      outcomes.zipWithIndex.foreach {
        case (Left(message), index) if index % 4 == 0 =>
          assertTrue(message.startsWith("illegal number"), message)
        case (Left(message), index) if index % 4 == 1 =>
          assertTrue(message.startsWith("unexpected end of input"), message)
        case (Right(snapshot), index) if index % 4 >= 2 =>
          assertEquals(snapshotFor(index), snapshot)
        case (other, index) =>
          throw new AssertionError(s"unexpected outcome $other for payload $index")
      }

      val recovered: Snapshot = submit(pool)(() => readFromString[Snapshot](writeToString(snapshotFor(5))))
      assertEquals(snapshotFor(5), recovered)
      val rejected: JsonReaderException = assertThrows(
        classOf[JsonReaderException],
        () => readFromString[Snapshot]("""{"warehouse":"wh-x","revision":1,"stock":[]} trailing""")
      )
      assertNotEquals(-1, rejected.getMessage.indexOf("expected end of input"), rejected.getMessage)
    } finally shutdown(pool)
  }
}

object InventoryWorkerPoolConcurrencyTest {

  /** One stocked item of a warehouse snapshot. */
  final case class Stock(sku: String, quantity: Int, bins: Seq[String])

  /** A warehouse inventory snapshot exchanged between workers. */
  final case class Snapshot(warehouse: String, revision: Long, stock: List[Stock])

  given snapshotCodec: JsonValueCodec[Snapshot] = JsonCodecMaker.make

  /** Builds a snapshot whose content is unique to `index`. */
  def snapshotFor(index: Int): Snapshot =
    Snapshot(
      s"wh-$index",
      index * 7L,
      (0 until (index % 5) + 1).map { i =>
        Stock(s"SKU-$index-$i", index * 10 + i, (0 until i).map(b => s"bin-$index-$b"))
      }.toList
    )

  /** Runs `count` tasks on `pool`, releasing them together, and returns results in index order. */
  def runAll[A](pool: ExecutorService, count: Int)(task: Int => A): Seq[A] = {
    val start: CountDownLatch = new CountDownLatch(1)
    val futures: Seq[Future[A]] = (0 until count).map { index =>
      pool.submit(new Callable[A] {
        override def call(): A = {
          start.await()
          task(index)
        }
      })
    }
    start.countDown()
    futures.map(_.get(60, TimeUnit.SECONDS))
  }

  /** Runs one task on `pool` and waits for its result. */
  def submit[A](pool: ExecutorService)(task: () => A): A =
    pool.submit(new Callable[A] {
      override def call(): A = task()
    }).get(60, TimeUnit.SECONDS)

  /** Stops `pool` and waits until its workers finish. */
  def shutdown(pool: ExecutorService): Unit = {
    pool.shutdown()
    assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
  }
}
