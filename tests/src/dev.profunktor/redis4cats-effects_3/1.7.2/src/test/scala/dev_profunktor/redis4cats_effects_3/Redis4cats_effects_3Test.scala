/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_effects_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.NoOp.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class Redis4cats_effects_3Test {
  @Test
  def utf8ClientExecutesStringHashSetListAndCounterCommands(): Unit = {
    val containerId: String = runDocker("run", "--rm", "-d", "-p", "127.0.0.1::6379", "valkey/valkey:7.2.6")
    try {
      val port: Int = runDocker(
        "inspect",
        "--format",
        "{{(index (index .NetworkSettings.Ports \"6379/tcp\") 0).HostPort}}",
        containerId
      ).toInt
      awaitReady(port)

      val result: (Option[String], Map[String, String], Set[String], List[String], Long) =
        Redis[IO].utf8(s"redis://127.0.0.1:$port?timeout=10s").use { redis =>
          for {
            _ <- redis.set("greeting", "hello")
            greeting <- redis.get("greeting")
            _ <- redis.hSet("profile", Map("name" -> "Redis4cats", "language" -> "Scala"))
            profile <- redis.hGetAll("profile")
            _ <- redis.sAdd("roles", "reader", "writer")
            roles <- redis.sMembers("roles")
            _ <- redis.rPush("events", "created", "updated")
            events <- redis.lRange("events", 0, -1)
            _ <- redis.set("counter", "4")
            counter <- redis.incrBy("counter", 3)
          } yield (greeting, profile, roles, events, counter)
        }.unsafeRunSync()

      assertEquals(Some("hello"), result._1)
      assertEquals(Map("name" -> "Redis4cats", "language" -> "Scala"), result._2)
      assertEquals(Set("reader", "writer"), result._3)
      assertEquals(List("created", "updated"), result._4)
      assertEquals(7L, result._5)
    } finally {
      runDocker("rm", "-f", containerId)
    }
  }

  private def awaitReady(port: Int): Unit = {
    val deadlineNanos: Long = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    var ready: Boolean = false
    while (!ready && System.nanoTime() < deadlineNanos) {
      val socket: Socket = new Socket()
      try {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 10000)
        ready = true
      } catch {
        case _: java.net.ConnectException => Thread.sleep(100)
      } finally {
        socket.close()
      }
    }
    assertEquals(true, ready, "Valkey did not become ready within 30 seconds")
  }

  private def runDocker(arguments: String*): String = {
    val process: Process = new ProcessBuilder(("docker" +: arguments)*).redirectErrorStream(true).start()
    val finished: Boolean = process.waitFor(30, TimeUnit.SECONDS)
    if (!finished) {
      process.destroyForcibly()
      process.waitFor(10, TimeUnit.SECONDS)
    }
    val output: String = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8).trim
    assertEquals(true, finished, s"Docker command timed out: docker ${arguments.mkString(" ")}")
    assertEquals(0, process.exitValue(), s"Docker command failed: docker ${arguments.mkString(" ")}\n$output")
    output
  }
}
