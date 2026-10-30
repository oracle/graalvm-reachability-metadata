/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.events.TestFailed
import org.scalatest.exceptions.NotSerializableWrapperException

@Timeout(120)
class EventTest {
  @Test
  def preservesSerializablePayloadWhenSocketReportingReplacesAThrowable(): Unit = {
    val (passed, events) = SocketFixtures.receive(classOf[FailingPayloadSuite])
    assertFalse(passed)
    val failures = events.collect { case event: TestFailed => event }
    assertEquals(1, failures.size)
    assertEquals("reports a failure with diagnostic context", failures.head.testName)
    assertEquals(Some("serializable context"), failures.head.payload)
    assertEquals("fixture failure", failures.head.message)
    assertTrue(failures.head.throwable.get.isInstanceOf[NotSerializableWrapperException])
  }
}
