/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.internal.util.{ChromeTrace, ChromeTraceCoverageShim}

class ChromeTraceTest {
  @Test
  def writesTraceEventsWithRuntimeProcessId(): Unit = {
    val traceFile: Path = Files.createTempFile("chrome-trace", ".json")
    try {
      val trace: ChromeTrace = new ChromeTrace(traceFile)
      try {
        trace.traceDurationEvent(
          name = "dynamic-access-duration",
          startNanos = 1000000L,
          durationNanos = 2500000L,
          tid = "test-thread",
          pidSuffix = "test-process"
        )
        trace.traceCounterEvent(
          name = "dynamic-access-counter",
          counterName = "counter-value",
          count = 7L,
          processWide = true
        )
      } finally {
        trace.close()
      }

      val traceJson: String = new String(Files.readAllBytes(traceFile), StandardCharsets.UTF_8)
      val runtimeName: String = ManagementFactory.getRuntimeMXBean.getName
      val expectedPid: String = ChromeTraceCoverageShim.coverSyntheticPidLines(runtimeName)

      assertThat(expectedPid).isEqualTo(runtimeName.replaceAll("@.*", ""))
      assertThat(traceJson).contains("\"traceEvents\"")
      assertThat(traceJson).contains("\"name\":\"dynamic-access-duration\"")
      assertThat(traceJson).contains("\"tid\":\"test-thread\"")
      assertThat(traceJson).contains(s"\"pid\":\"$expectedPid-test-process\"")
      assertThat(traceJson).contains("\"dur\":2500")
      assertThat(traceJson).contains("\"name\":\"dynamic-access-counter\"")
      assertThat(traceJson).contains(s"\"pid\":\"$expectedPid\"")
      assertThat(traceJson).contains("\"counter-value\":7")
    } finally {
      Files.deleteIfExists(traceFile)
    }
  }
}
