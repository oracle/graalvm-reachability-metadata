/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.case_insensitive_3

import cats.Show
import cats.kernel.Hash
import cats.kernel.LowerBounded
import cats.kernel.Monoid
import cats.kernel.Order
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

class Case_insensitive_3Test {
  @Test
  def equalityHashingAndOriginalTextPreservationAreCaseInsensitive(): Unit = {
    val lower: CIString = CIString("content-type")
    val mixed: CIString = CIString("Content-Type")
    val other: CIString = CIString("Content-Length")

    assertEquals(lower, mixed)
    assertEquals(lower.hashCode(), mixed.hashCode())
    assertNotEquals(lower, other)
    assertNotEquals(lower, "content-type")
    assertEquals("Content-Type", mixed.toString)

    val headers: Set[CIString] = Set(lower, mixed, other)
    assertEquals(2, headers.size)
    assertTrue(headers.contains(CIString("CONTENT-TYPE")))
    assertTrue(headers.contains(CIString("content-length")))
  }

  @Test
  def orderingAndComparisonIgnoreCaseWithoutChangingStoredValue(): Unit = {
    val alpha: CIString = CIString("Alpha")
    val alphaUpper: CIString = CIString("ALPHA")
    val beta: CIString = CIString("beta")

    assertEquals(0, alpha.compare(alphaUpper))
    assertTrue(alpha <= alphaUpper)
    assertTrue(alpha >= alphaUpper)
    assertTrue(alpha < beta)
    assertTrue(beta > alphaUpper)

    val sorted: List[String] = List(beta, CIString("gamma"), alphaUpper, CIString("Delta")).sorted.map(_.toString)
    assertEquals(List("ALPHA", "beta", "Delta", "gamma"), sorted)
  }

  @Test
  def stringLikeOperationsReturnCaseInsensitiveValues(): Unit = {
    val padded: CIString = CIString("  X-Trace-ID  ")
    val trimmed: CIString = padded.trim
    val transformed: CIString = trimmed.transform(name => s"$name:123")

    assertFalse(CIString.empty.nonEmpty)
    assertTrue(CIString.empty.isEmpty)
    assertTrue(trimmed.nonEmpty)
    assertEquals(10, trimmed.length)
    assertEquals("X-Trace-ID", trimmed.toString)
    assertEquals(CIString("x-trace-id:123"), transformed)
    assertEquals("X-Trace-ID:123", transformed.toString)

    val headerLine: CIString = CIString("Accept-Encoding: br, gzip")
    assertTrue(headerLine.contains(CIString("accept-encoding")))
    assertTrue(headerLine.contains(CIString("BR, GZIP")))
    assertFalse(headerLine.contains(CIString("content-type")))
  }

  @Test
  def catsTypeclassInstancesUseCaseInsensitiveSemantics(): Unit = {
    val first: CIString = CIString("ETag")
    val second: CIString = CIString(": W/\"abc\"")
    val combined: CIString = Monoid[CIString].combine(first, second)

    assertEquals(CIString.empty, Monoid[CIString].empty)
    assertEquals(CIString("ETag: W/\"abc\""), combined)
    assertEquals("ETag: W/\"abc\"", Show[CIString].show(combined))
    assertEquals(CIString("ETag: W/\"abc\""), Monoid[CIString].combineAll(List(first, second)))

    assertEquals(0, Order[CIString].compare(CIString("host"), CIString("HOST")))
    assertTrue(Order[CIString].lt(CIString("accept"), CIString("host")))
    assertEquals(Hash[CIString].hash(CIString("vary")), Hash[CIString].hash(CIString("VARY")))
    assertEquals(CIString.empty, LowerBounded[CIString].minBound)
  }

  @Test
  def ciInterpolatorBuildsCaseInsensitiveStringsWithSubstitutions(): Unit = {
    val method: String = "GET"
    val path: String = "/Api/Items"
    val requestLine: CIString = ci"$method $path HTTP/1.1"

    assertEquals(CIString("get /api/items http/1.1"), requestLine)
    assertEquals("GET /Api/Items HTTP/1.1", requestLine.toString)
    assertTrue(requestLine.contains(ci"/api"))
  }

  @Test
  def ciExtractorMatchesCaseInsensitivePrefixesAndCapturesOriginalText(): Unit = {
    val authorization: CIString = CIString("Bearer AbCd-123")

    authorization match {
      case ci"bearer $token" =>
        assertEquals(CIString("abcd-123"), token)
        assertEquals("AbCd-123", token.toString)
      case _ => throw new AssertionError("Expected bearer token to match case-insensitively")
    }

    CIString("Basic AbCd-123") match {
      case ci"bearer $token" => throw new AssertionError(s"Unexpected bearer token match: $token")
      case _ => assertTrue(true)
    }
  }

  @Test
  def ciExtractorSupportsMultipleWildcardsAndZeroLengthCaptures(): Unit = {
    val statusLine: CIString = CIString("HTTP/1.1 404 Not Found")

    statusLine match {
      case ci"http/$version $code $reason" =>
        assertEquals(CIString("1.1"), version)
        assertEquals(CIString("404"), code)
        assertEquals(CIString("not found"), reason)
        assertEquals("Not Found", reason.toString)
      case _ => throw new AssertionError("Expected status line to match")
    }

    CIString("prefixSUFFIX") match {
      case ci"prefix${middle}suffix" => assertTrue(middle.isEmpty)
      case _ => throw new AssertionError("Expected zero-length capture between adjacent chunks")
    }
  }
}
