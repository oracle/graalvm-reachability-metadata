/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_core_3

import fs2.Pure
import org.http4s.Request
import org.http4s.ember.core.EmberException
import org.http4s.ember.core.h2.H2Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.time.Instant
import scala.concurrent.duration.DurationInt

class Http4s_ember_core_3Test {
  @Test
  def emberExceptionsExposeStableMessagesAndTypes(): Unit = {
    val started: Instant = Instant.parse("2024-01-01T00:00:00Z")
    val timedOut: Instant = Instant.parse("2024-01-01T00:00:05Z")

    val timeout: EmberException.Timeout = EmberException.Timeout(started, timedOut)
    assertEquals(
      "Timeout Occured - Started: 2024-01-01T00:00:00Z, Timed Out: 2024-01-01T00:00:05Z",
      timeout.getMessage,
    )
    assertEquals(started, timeout.started)
    assertEquals(timedOut, timeout.timedOut)
    assertEquals(timeout, timeout.copy(timedOut = timedOut))

    val incomplete: EmberException.IncompleteClientRequest =
      EmberException.IncompleteClientRequest("Host header")
    assertInstanceOf(classOf[IllegalArgumentException], incomplete)
    assertEquals("Incomplete Client Request: Mising Host header", incomplete.getMessage)
    assertEquals("Host header", incomplete.missing)

    val parseError: EmberException.ParseError = EmberException.ParseError("bad status line")
    val chunkedError: EmberException.ChunkedEncodingError =
      EmberException.ChunkedEncodingError("bad chunk header")
    assertEquals("bad status line", parseError.getMessage)
    assertEquals("bad chunk header", chunkedError.getMessage)
  }

  @Test
  def streamBoundaryExceptionsReportParsingFailures(): Unit = {
    val emptyStream: EmberException.EmptyStream = EmberException.EmptyStream()
    val reachedEnd: EmberException.ReachedEndOfStream = EmberException.ReachedEndOfStream()
    val messageTooLong: EmberException.MessageTooLong = EmberException.MessageTooLong(8192)
    val readTimeout: EmberException.ReadTimeout = EmberException.ReadTimeout(250.millis)

    assertEquals("Cannot Parse Empty Stream", emptyStream.getMessage)
    assertEquals("Reached End Of Stream While Reading", reachedEnd.getMessage)
    assertEquals("HTTP Header Section Exceeds Max Size: 8192 Bytes", messageTooLong.getMessage)
    assertEquals("Read timeout after 250 milliseconds", readTimeout.getMessage)

    val failures: List[EmberException] =
      List(emptyStream, reachedEnd, messageTooLong, readTimeout)
    assertTrue(failures.forall(_.isInstanceOf[RuntimeException]))
    assertEquals(4, failures.map(_.productPrefix).distinct.size)
  }

  @Test
  def h2KeysStoreIndependentTypedRequestAttributes(): Unit = {
    val pushedRequest: Request[Pure] = Request[Pure]()
    val pushPromises: List[Request[Pure]] = List(pushedRequest)

    val request: Request[Pure] = Request[Pure]()
      .withAttribute(H2Keys.StreamIdentifier, 7)
      .withAttribute(H2Keys.PushPromiseInitialStreamIdentifier, 9)
      .withAttribute(H2Keys.PushPromises, pushPromises)

    assertEquals(Some(7), request.attributes.lookup(H2Keys.StreamIdentifier))
    assertEquals(Some(9), request.attributes.lookup(H2Keys.PushPromiseInitialStreamIdentifier))
    assertEquals(Some(pushPromises), request.attributes.lookup(H2Keys.PushPromises))

    assertNotSame(H2Keys.StreamIdentifier, H2Keys.PushPromiseInitialStreamIdentifier)
    assertFalse(request.attributes.lookup(H2Keys.StreamIdentifier).contains(9))
    assertFalse(request.attributes.lookup(H2Keys.PushPromiseInitialStreamIdentifier).contains(7))
  }

  @Test
  def h2PushPromisesPreserveOrderedPromisedRequestMetadata(): Unit = {
    val firstPushedRequest: Request[Pure] = Request[Pure]()
      .withAttribute(H2Keys.StreamIdentifier, 11)
    val secondPushedRequest: Request[Pure] = Request[Pure]()
      .withAttribute(H2Keys.StreamIdentifier, 13)
      .withAttribute(H2Keys.PushPromiseInitialStreamIdentifier, 7)
    val pushPromises: List[Request[Pure]] = List(firstPushedRequest, secondPushedRequest)

    val request: Request[Pure] = Request[Pure]()
      .withAttribute(H2Keys.PushPromises, pushPromises)

    val storedPushPromises: Option[List[Request[Pure]]] =
      request.attributes.lookup(H2Keys.PushPromises)

    assertEquals(Some(pushPromises), storedPushPromises)
    assertEquals(
      List(Some(11), Some(13)),
      storedPushPromises.getOrElse(Nil).map(_.attributes.lookup(H2Keys.StreamIdentifier)),
    )
    assertEquals(
      List(None, Some(7)),
      storedPushPromises.getOrElse(Nil).map(
        _.attributes.lookup(H2Keys.PushPromiseInitialStreamIdentifier)
      ),
    )
  }

  @Test
  def h2KeysCanBeDeletedWithoutAffectingOtherAttributes(): Unit = {
    val initialRequest: Request[Pure] = Request[Pure]()
      .withAttribute(H2Keys.StreamIdentifier, 3)
      .withAttribute(H2Keys.PushPromiseInitialStreamIdentifier, 5)

    val updatedRequest: Request[Pure] = initialRequest.withoutAttribute(H2Keys.StreamIdentifier)

    assertEquals(None, updatedRequest.attributes.lookup(H2Keys.StreamIdentifier))
    assertEquals(Some(5), updatedRequest.attributes.lookup(H2Keys.PushPromiseInitialStreamIdentifier))
    assertEquals(
      initialRequest.attributes.lookup(H2Keys.PushPromiseInitialStreamIdentifier),
      updatedRequest.attributes.lookup(H2Keys.PushPromiseInitialStreamIdentifier),
    )
  }
}
