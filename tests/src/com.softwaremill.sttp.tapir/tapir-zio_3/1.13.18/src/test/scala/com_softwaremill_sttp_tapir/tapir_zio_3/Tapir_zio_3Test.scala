/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertSame, assertTrue}
import org.junit.jupiter.api.Test
import sttp.model.sse.ServerSentEvent
import sttp.tapir.ztapir.*
import zio.stream.ZStream
import zio.{RIO, Runtime, Unsafe, ZIO}

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class Tapir_zio_3Test {
  private val monad: RIOMonadError[Any] = new RIOMonadError[Any]

  @Test
  def rioMonadErrorRunsSuccessfulFailedAndFinalizedEffects(): Unit = {
    val finalized: AtomicInteger = new AtomicInteger(0)
    val plannedFailure: IllegalArgumentException = new IllegalArgumentException("planned")

    val successful: RIO[Any, Int] = monad.flatMap(monad.unit(20)) { first =>
      monad.map(monad.eval(21))(second => first + second)
    }
    val suspended: RIO[Any, Int] = monad.suspend(monad.unit(1))
    val flattened: RIO[Any, Int] = monad.flatten(monad.unit(monad.unit(2)))
    val blocking: RIO[Any, String] = monad.blocking("blocking-result")
    val recovered: RIO[Any, Int] = monad.handleError(monad.error[Int](plannedFailure)) {
      case e: IllegalArgumentException if e.getMessage == "planned" => monad.unit(41)
    }
    val finalizedValue: RIO[Any, String] = monad.ensure2(
      monad.unit("done"),
      ZIO.succeed(finalized.incrementAndGet()).unit
    )

    assertEquals(41, unsafeRun(successful))
    assertEquals(1, unsafeRun(suspended))
    assertEquals(2, unsafeRun(flattened))
    assertEquals("blocking-result", unsafeRun(blocking))
    assertEquals(41, unsafeRun(recovered))
    assertEquals("done", unsafeRun(finalizedValue))
    assertEquals(1, finalized.get())
  }

  @Test
  def publicZServerLogicKeepsEndpointDescriptionAndReturnsTypedResults(): Unit = {
    val api = endpoint.get
      .in("hello")
      .in(query[String]("name"))
      .errorOut(stringBody)
      .out(stringBody)
      .name("hello-endpoint")

    val serverEndpoint = api.zServerLogic[Any] { name =>
      if name.nonEmpty then ZIO.succeed(s"Hello, $name!")
      else ZIO.fail("empty-name")
    }
    val widened = serverEndpoint.widen[Any]

    assertSame(api, serverEndpoint.endpoint)
    assertSame(serverEndpoint.endpoint, widened.endpoint)
    assertEquals("hello-endpoint", serverEndpoint.info.name.getOrElse(""))
    assertEquals(Right(()), unsafeRun(serverEndpoint.securityLogic(monad)(())))
    assertEquals(Right("Hello, Ada!"), unsafeRun(serverEndpoint.logic(monad)(())("Ada")))
    assertEquals(Left("empty-name"), unsafeRun(serverEndpoint.logic(monad)(())("")))
  }

  @Test
  def securedZPartialServerEndpointCanBeExtendedAndRunAsServerEndpoint(): Unit = {
    val baseEndpoint = endpoint
      .securityIn(header[String]("Authorization"))
      .errorOut(stringBody)
      .description("secured base endpoint")

    val partial = baseEndpoint.zServerSecurityLogic[Any, String] {
      case "Bearer secret" => ZIO.succeed("alice")
      case _               => ZIO.fail("forbidden")
    }

    val completed = partial
      .in("numbers")
      .in(query[Int]("n"))
      .out(stringBody)
      .summary("double a number")
      .serverLogic[Any] { principal => number =>
        if number >= 0 then ZIO.succeed(s"$principal:${number * 2}")
        else ZIO.fail("negative")
      }

    assertEquals(Some("secured base endpoint"), partial.info.description)
    assertEquals(Some("double a number"), completed.info.summary)
    assertEquals(Right("alice"), unsafeRun(completed.securityLogic(monad)("Bearer secret")))
    assertEquals(Left("forbidden"), unsafeRun(completed.securityLogic(monad)("Bearer wrong")))
    assertEquals(Right("alice:42"), unsafeRun(completed.logic(monad)("alice")(21)))
    assertEquals(Left("negative"), unsafeRun(completed.logic(monad)("alice")(-1)))
  }

  @Test
  def zioServerSentEventsSerialiseAndParseByteStreams(): Unit = {
    val events: List[ServerSentEvent] = List(
      ServerSentEvent(
        data = Some("first line\nsecond line"),
        eventType = Some("joined"),
        id = Some("event-1"),
        retry = Some(250)
      ),
      ServerSentEvent(data = Some("tail"), eventType = Some("single"))
    )

    val bytes = unsafeRun(
      ZioServerSentEvents
        .serialiseSSEToBytes(ZStream.fromIterable(events))
        .runCollect
    )
    val text: String = new String(bytes.toArray, StandardCharsets.UTF_8)

    assertTrue(text.contains("data: first line\ndata: second line"))
    assertTrue(text.contains("event: joined"))
    assertTrue(text.contains("id: event-1"))
    assertTrue(text.contains("retry: 250"))

    val parsed = unsafeRun(
      ZioServerSentEvents
        .parseBytesToSSE(ZStream.fromIterable(bytes.toArray.toIndexedSeq))
        .runCollect
    ).toList

    assertEquals(events, parsed)
  }

  private def unsafeRun[A](effect: ZIO[Any, Throwable, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }
}
