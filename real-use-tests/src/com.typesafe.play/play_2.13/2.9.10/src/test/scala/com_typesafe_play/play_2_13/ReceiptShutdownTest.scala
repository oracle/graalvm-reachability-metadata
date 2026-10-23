/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}

import java.io.{BufferedWriter, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play application lifecycle hooks to drain buffered receipts before closing their journal. */
@Timeout(60)
class ReceiptShutdownTest {
  case class Receipt(orderId: String, totalCents: Int)

  class ReceiptJournal(path: Path, lifecycle: ApplicationLifecycle) extends AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(path, UTF_8)

    lifecycle.addStopHook { () => Future.successful(close()) }

    def record(receipt: Receipt): Unit = {
      writer.write(s"${receipt.orderId},${receipt.totalCents}\n")
    }

    override def close(): Unit = writer.close()
  }

  class BufferedReceipts(journal: ReceiptJournal, lifecycle: ApplicationLifecycle)(
      implicit executionContext: ExecutionContext
  ) {
    private val pending: ListBuffer[Receipt] = ListBuffer.empty

    lifecycle.addStopHook { () =>
      Future {
        pending.foreach(journal.record)
        pending.clear()
      }
    }

    def accept(receipt: Receipt): Unit = {
      pending += receipt
    }
  }

  @Test
  def persistsAcceptedReceiptsBeforeShutdownCompletesAndReleasesTheWriter(): Unit = {
    implicit val executionContext: ExecutionContext = ExecutionContext.global
    val lifecycle: DefaultApplicationLifecycle = new DefaultApplicationLifecycle()
    val path: Path = Files.createTempFile("receipt-journal-", ".csv")
    val journal: ReceiptJournal = new ReceiptJournal(path, lifecycle)

    try {
      val receipts: BufferedReceipts = new BufferedReceipts(journal, lifecycle)
      receipts.accept(Receipt("ORD-42", 7500))
      receipts.accept(Receipt("ORD-43", 4200))
      receipts.accept(Receipt("ORD-44", 1800))
      assertEquals("", Files.readString(path, UTF_8))

      Await.result(lifecycle.stop(), 10.seconds)

      assertEquals("ORD-42,7500\nORD-43,4200\nORD-44,1800\n", Files.readString(path, UTF_8))
      assertThrows(classOf[IOException], () => journal.record(Receipt("ORD-45", 2500)))
    } finally {
      try {
        Await.result(lifecycle.stop(), 10.seconds)
      } finally {
        try {
          journal.close()
        } finally {
          Files.deleteIfExists(path)
        }
      }
    }
  }
}
