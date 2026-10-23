/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.io.TempDir
import zio.{Duration, Runtime, Task, Unsafe, ZIO}

/** Exercises ZIO asynchronous callback integration by importing shipment manifests through asynchronous file reads. */
class AsyncShipmentManifestTest {
  private case class Shipment(orderId: String, destination: String, parcels: Int)

  private def readChunk(channel: AsynchronousFileChannel, buffer: ByteBuffer, position: Long): Task[Int] =
    ZIO.async[Any, Throwable, Int] { complete =>
      channel.read(buffer, position, (), new CompletionHandler[Integer, Unit] {
        override def completed(count: Integer, attachment: Unit): Unit =
          complete(ZIO.succeed(count.intValue()))

        override def failed(error: Throwable, attachment: Unit): Unit =
          complete(ZIO.fail(error))
      })
    }

  private def readContents(channel: AsynchronousFileChannel): Task[String] = ZIO.suspendSucceed {
    // Records cross buffer boundaries; decode only after all bytes have arrived.
    val buffer: ByteBuffer = ByteBuffer.allocate(11)
    val contents: ByteArrayOutputStream = new ByteArrayOutputStream()

    def next(position: Long): Task[String] = for {
      _ <- ZIO.succeed(buffer.clear())
      count <- readChunk(channel, buffer, position)
      result <- if (count < 0) ZIO.succeed(contents.toString(UTF_8))
        else ZIO.succeed(contents.write(buffer.array(), 0, count)) *>
          ZIO.suspendSucceed(next(position + count))
    } yield result

    next(0L)
  }

  private def importManifest(path: Path): Task[Vector[Shipment]] = ZIO.scoped {
    for {
      channel <- ZIO.acquireRelease(
        ZIO.attempt(AsynchronousFileChannel.open(path, StandardOpenOption.READ))
      )(channel => ZIO.attempt(channel.close()).orDie)
      contents <- readContents(channel)
      shipments <- ZIO.attempt {
        contents.linesIterator.filter(_.nonEmpty).map { line =>
          val fields: Array[String] = line.split(",")
          require(fields.length == 3, "Manifest rows require order, destination and parcel count")
          val shipment: Shipment = Shipment(fields(0), fields(1), fields(2).toInt)
          require(shipment.parcels > 0, "Parcel count must be positive")
          shipment
        }.toVector
      }
    } yield shipments
  }

  @Test
  @Timeout(60)
  def callbackReadsPreserveCompleteRecordsAndHandleEmptyManifests(@TempDir directory: Path): Unit = {
    val manifest: Path = directory.resolve("shipments.csv")
    val empty: Path = directory.resolve("empty.csv")
    Files.writeString(manifest, "ORDER-701,DE,2\nORDER-702,FR,1\nORDER-703,DE,3\n", UTF_8)
    Files.writeString(empty, "", UTF_8)

    val program: Task[List[Vector[Shipment]]] =
      ZIO.foreachPar(List(manifest, empty))(importManifest)
    val results: List[Vector[Shipment]] = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        program.timeoutFail(new IllegalStateException("Manifest import timed out"))(Duration.fromSeconds(30))
      ).getOrThrowFiberFailure()
    }

    assertEquals(
      Vector(Shipment("ORDER-701", "DE", 2), Shipment("ORDER-702", "FR", 1), Shipment("ORDER-703", "DE", 3)),
      results.head
    )
    assertEquals(Map("DE" -> 5, "FR" -> 1), results.head.groupMapReduce(_.destination)(_.parcels)(_ + _))
    assertEquals(Vector.empty[Shipment], results(1))
  }
}
