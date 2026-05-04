/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_interop_tracer_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import zio.internal.stacktracer.InteropTracer

class AkkaLineNumbersTest {
  @Test
  def newTraceReadsAnOrdinaryObjectClassResource(): Unit = {
    val target: PlainTarget = new PlainTarget("ordinary-object")

    val trace: AnyRef = InteropTracer.newTrace(target).asInstanceOf[AnyRef]

    assertNotNull(trace)
    assertSame(trace, InteropTracer.newTrace(new PlainTarget("second-object")).asInstanceOf[AnyRef])
  }

  @Test
  def newTraceReadsASerializableLambdaImplementationClassResource(): Unit = {
    val target: SerializableThunk = () => "serializable-lambda"

    assertEquals("serializable-lambda", target.value())
    val trace: AnyRef = InteropTracer.newTrace(target).asInstanceOf[AnyRef]

    assertNotNull(trace)
    assertSame(trace, InteropTracer.newTrace(target).asInstanceOf[AnyRef])
  }

  private final class PlainTarget(val name: String)

  private trait SerializableThunk extends Serializable {
    def value(): String
  }
}
