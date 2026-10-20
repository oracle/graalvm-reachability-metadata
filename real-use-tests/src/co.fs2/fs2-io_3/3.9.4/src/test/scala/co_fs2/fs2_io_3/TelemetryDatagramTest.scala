/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.{Chunk, Stream}
import fs2.io.net.{Datagram, DatagramSocket, Network}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.*

/** Exercises UDP datagrams by collecting sensor telemetry and acknowledging each complete reading. */
class TelemetryDatagramTest {
  private case class Reading(station: String, sequence: Int, temperature: Int) {
    def wire: String = s"$station,$sequence,$temperature"
    def acknowledgment: String = s"ACK:$station:$sequence"
  }

  private def decode(bytes: Chunk[Byte]): String = new String(bytes.toArray, UTF_8)

  @Test
  @Timeout(60)
  def collectAndAcknowledgeSensorReadings(): Unit = {
    val readings: List[Reading] = List(
      Reading("north-roof", 1, 18),
      Reading("cellar", 1, 12),
      Reading("north-roof", 2, 20)
    )
    val network: Network[IO] = Network[IO]
    val sockets: Resource[IO, (DatagramSocket[IO], DatagramSocket[IO])] = for {
      gateway <- network.openDatagramSocket(Some(ip"127.0.0.1"))
      sensor <- network.openDatagramSocket(Some(ip"127.0.0.1"))
    } yield (gateway, sensor)

    sockets.use { case (gateway: DatagramSocket[IO], sensor: DatagramSocket[IO]) =>
      for {
        gatewayAddress <- gateway.localAddress
        sensorAddress <- sensor.localAddress
        result <- {
          val collect: IO[List[Reading]] = gateway.reads.take(readings.size.toLong)
            .evalMap { datagram =>
              for {
                reading <- IO {
                  assertEquals(sensorAddress, datagram.remote, "Replies must target the sending sensor")
                  val fields: Array[String] = decode(datagram.bytes).split(",")
                  assertEquals(3, fields.length, "One datagram must contain one complete reading")
                  Reading(fields(0), fields(1).toInt, fields(2).toInt)
                }
                _ <- gateway.write(Datagram(
                  datagram.remote,
                  Chunk.array(reading.acknowledgment.getBytes(UTF_8))
                ))
              } yield reading
            }.compile.toList

          val transmit: IO[List[String]] = Stream.emits(readings).covary[IO]
            .evalMap { reading =>
              for {
                _ <- sensor.write(Datagram(gatewayAddress, Chunk.array(reading.wire.getBytes(UTF_8))))
                reply <- sensor.read
                _ <- IO(assertEquals(gatewayAddress, reply.remote, "Only the gateway may acknowledge readings"))
              } yield decode(reply.bytes)
            }.compile.toList

          collect.both(transmit).timeout(20.seconds)
        }
        _ <- IO {
          val (received, acknowledgments) = result
          assertEquals(readings, received, "Telemetry must retain message boundaries and values")
          assertEquals(readings.map(_.acknowledgment), acknowledgments)
          val latest: Map[String, Int] = received.map(reading => reading.station -> reading.temperature).toMap
          assertEquals(Map("north-roof" -> 20, "cellar" -> 12), latest)
        }
      } yield ()
    }.unsafeRunSync()
  }
}
