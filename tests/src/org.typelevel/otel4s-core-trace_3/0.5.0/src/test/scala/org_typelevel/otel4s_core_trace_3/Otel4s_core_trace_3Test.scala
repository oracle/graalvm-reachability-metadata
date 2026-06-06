/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.otel4s_core_trace_3

import cats.Id
import cats.Semigroup
import cats.arrow.FunctionK
import cats.effect.kernel.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.AttributeKey
import org.typelevel.otel4s.AttributeType
import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.context.propagation.TextMapUpdater
import org.typelevel.otel4s.meta.InstrumentMeta
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.SpanBuilder
import org.typelevel.otel4s.trace.SpanContext
import org.typelevel.otel4s.trace.SpanFinalizer
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.SpanOps
import org.typelevel.otel4s.trace.Status
import org.typelevel.otel4s.trace.TraceFlags
import org.typelevel.otel4s.trace.TraceState
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.trace.TracerProvider

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class Otel4s_core_trace_3Test {
  @Test
  def traceFlagsConvertBetweenBytesHexAndSamplingState(): Unit = {
    val defaultFlags: TraceFlags = TraceFlags.Default
    val sampled: TraceFlags = TraceFlags.Sampled
    val customSampled: TraceFlags = TraceFlags.fromByte(0x03.toByte)

    assertEquals(0x00.toByte, defaultFlags.toByte)
    assertEquals("00", defaultFlags.toHex)
    assertFalse(defaultFlags.isSampled)

    assertEquals(0x01.toByte, sampled.toByte)
    assertEquals("01", sampled.toHex)
    assertTrue(sampled.isSampled)

    assertEquals(Some(sampled), TraceFlags.fromHex("01"))
    assertEquals(Some(defaultFlags), TraceFlags.fromHex("00"))
    assertEquals("03", customSampled.toHex)
    assertTrue(customSampled.isSampled)
    assertEquals(None, TraceFlags.fromHex("zz"))
    assertEquals(sampled, TraceFlags.fromByte(1.toByte))
    assertNotEquals(sampled, defaultFlags)
    assertEquals("01", sampled.toString)
  }

  @Test
  def traceStateMaintainsImmutableOrderedValidatedEntries(): Unit = {
    val base: TraceState = TraceState.empty
      .updated("vendor", "one")
      .updated("tenant@vendor", "two")
      .updated("vendor", "three")

    assertFalse(base.isEmpty)
    assertEquals(2, base.size)
    assertEquals(Some("three"), base.get("vendor"))
    assertEquals(Some("two"), base.get("tenant@vendor"))
    assertEquals(None, base.get("missing"))
    assertEquals(List("vendor", "tenant@vendor"), base.asMap.keys.toList)
    assertEquals("TraceState{entries={vendor=three,tenant@vendor=two}}", base.toString)

    val rejected: TraceState = base
      .updated("UpperCase", "ignored")
      .updated("1bad", "ignored")
      .updated("valid", "bad,value")
      .updated("also-valid", "bad=value")
      .updated("empty", "")

    assertEquals(base, rejected)

    val removed: TraceState = base.removed("vendor")
    assertEquals(1, removed.size)
    assertEquals(None, removed.get("vendor"))
    assertEquals(Some("two"), removed.get("tenant@vendor"))
    assertEquals(2, base.size)
  }

  @Test
  def traceStateRejectsEntriesBeyondW3CLimitButAllowsExistingKeyUpdate(): Unit = {
    val full: TraceState = (1 to 32).foldLeft(TraceState.empty) { (state: TraceState, index: Int) =>
      state.updated(s"k$index", s"v$index")
    }

    val withOverflow: TraceState = full.updated("overflow", "ignored")
    val withExistingUpdated: TraceState = full.updated("k16", "updated")

    assertEquals(32, full.size)
    assertEquals(32, withOverflow.size)
    assertEquals(None, withOverflow.get("overflow"))
    assertEquals(32, withExistingUpdated.size)
    assertEquals(Some("updated"), withExistingUpdated.get("k16"))
    assertEquals("k16", withExistingUpdated.asMap.keys.head)
  }

  @Test
  def spanContextValidatesIdentifiersAndExposesStablePresentation(): Unit = {
    val traceId = SpanContext.TraceId.fromLongs(0x0102030405060708L, 0x1112131415161718L)
    val spanId = SpanContext.SpanId.fromLong(0x2122232425262728L)
    val traceState: TraceState = TraceState.empty.updated("vendor", "state")
    val context: SpanContext = SpanContext(
      traceId = traceId,
      spanId = spanId,
      traceFlags = TraceFlags.Sampled,
      traceState = traceState,
      remote = true
    )

    assertTrue(SpanContext.TraceId.isValid(traceId))
    assertTrue(SpanContext.SpanId.isValid(spanId))
    assertTrue(context.isValid)
    assertTrue(context.isRemote)
    assertTrue(context.isSampled)
    assertEquals(traceId, context.traceId)
    assertEquals("01020304050607081112131415161718", context.traceIdHex)
    assertEquals(spanId, context.spanId)
    assertEquals("2122232425262728", context.spanIdHex)
    assertEquals(TraceFlags.Sampled, context.traceFlags)
    assertEquals(traceState, context.traceState)
    assertEquals(context, SpanContext(traceId, spanId, TraceFlags.Sampled, traceState, remote = true))
    assertTrue(context.toString.contains("valid=true"))
  }

  @Test
  def spanContextReplacesInvalidIdentifiersWithCanonicalInvalidValues(): Unit = {
    val validSpanId = SpanContext.SpanId.fromLong(0x0102030405060708L)
    val invalidBecauseTraceIdIsZero: SpanContext = SpanContext(
      traceId = SpanContext.TraceId.Invalid,
      spanId = validSpanId,
      traceFlags = TraceFlags.Sampled,
      traceState = TraceState.empty.updated("vendor", "state"),
      remote = true
    )

    assertFalse(SpanContext.TraceId.isValid(SpanContext.TraceId.Invalid))
    assertFalse(SpanContext.SpanId.isValid(SpanContext.SpanId.Invalid))
    assertFalse(invalidBecauseTraceIdIsZero.isValid)
    assertTrue(invalidBecauseTraceIdIsZero.isRemote)
    assertTrue(invalidBecauseTraceIdIsZero.isSampled)
    assertEquals(SpanContext.TraceId.Invalid, invalidBecauseTraceIdIsZero.traceId)
    assertEquals(SpanContext.TraceId.InvalidHex, invalidBecauseTraceIdIsZero.traceIdHex)
    assertEquals(SpanContext.SpanId.Invalid, invalidBecauseTraceIdIsZero.spanId)
    assertEquals(SpanContext.SpanId.InvalidHex, invalidBecauseTraceIdIsZero.spanIdHex)
    assertEquals(SpanContext.invalid, SpanContext.invalid)
    assertNotEquals(SpanContext.invalid, invalidBecauseTraceIdIsZero)
  }

  @Test
  def attributesAndEnumerationsExposeTypedValuesHashingAndShowInstances(): Unit = {
    val stringKey: AttributeKey[String] = AttributeKey.string("http.method")
    val retryKey: AttributeKey[Long] = AttributeKey.long("retry.count")
    val tagsKey: AttributeKey[List[String]] = AttributeKey.stringList("tags")
    val method: Attribute[String] = stringKey("GET")
    val retry: Attribute[Long] = Attribute("retry.count", 2L)
    val tags: Attribute[List[String]] = tagsKey(List("blue", "green"))

    assertEquals("http.method", stringKey.name)
    assertSame(AttributeType.String, stringKey.`type`)
    assertEquals(AttributeKey.string("http.method"), stringKey)
    assertEquals(method, Attribute("http.method", "GET"))
    assertEquals(retryKey(2L), retry)
    assertEquals(List("blue", "green"), tags.value)
    assertEquals("String(http.method)", stringKey.toString)
    assertEquals("Attribute(Long(retry.count),2)", retry.toString)

    assertSame(Status.Unset, Status.Unset)
    assertSame(Status.Ok, Status.Ok)
    assertSame(Status.Error, Status.Error)
    assertEquals("Internal", SpanKind.Internal.toString)
    assertEquals("Server", SpanKind.Server.toString)
    assertEquals("Client", SpanKind.Client.toString)
    assertEquals("Producer", SpanKind.Producer.toString)
    assertEquals("Consumer", SpanKind.Consumer.toString)
  }

  @Test
  def spanFinalizersRepresentStatusExceptionsAttributesAndComposition(): Unit = {
    val failure = new IllegalStateException("boom")
    val errorStatus: SpanFinalizer = SpanFinalizer.setStatus(Status.Error, "failed")
    val exception: SpanFinalizer = SpanFinalizer.recordException(failure)
    val attributes: SpanFinalizer = SpanFinalizer.addAttributes(
      Attribute("component", "test"),
      Attribute("attempt", 1L)
    )
    val combined: SpanFinalizer = Semigroup[SpanFinalizer].combine(errorStatus, exception)
    val multiple: SpanFinalizer.Multiple = SpanFinalizer.multiple(combined, attributes)

    val statusFinalizer = errorStatus.asInstanceOf[SpanFinalizer.SetStatus]
    assertSame(Status.Error, statusFinalizer.status)
    assertEquals(Some("failed"), statusFinalizer.description)

    val exceptionFinalizer = exception.asInstanceOf[SpanFinalizer.RecordException]
    assertSame(failure, exceptionFinalizer.throwable)

    val attributeFinalizer = attributes.asInstanceOf[SpanFinalizer.AddAttributes]
    assertEquals(2, attributeFinalizer.attributes.size)
    assertEquals(Attribute("component", "test"), attributeFinalizer.attributes.head)

    val combinedFinalizers = combined.asInstanceOf[SpanFinalizer.Multiple].finalizers
    assertEquals(List(errorStatus, exception), combinedFinalizers.toList)
    assertEquals(List(combined, attributes), multiple.finalizers.toList)

    val abnormalError: SpanFinalizer = SpanFinalizer.Strategy.reportAbnormal(
      cats.effect.kernel.Resource.ExitCase.Errored(failure)
    )
    assertEquals(2, abnormalError.asInstanceOf[SpanFinalizer.Multiple].finalizers.toList.size)

    val abnormalCancellation: SpanFinalizer.SetStatus = SpanFinalizer.Strategy
      .reportAbnormal(cats.effect.kernel.Resource.ExitCase.Canceled)
      .asInstanceOf[SpanFinalizer.SetStatus]
    assertSame(Status.Error, abnormalCancellation.status)
    assertEquals(Some("canceled"), abnormalCancellation.description)
    assertFalse(SpanFinalizer.Strategy.empty.isDefinedAt(cats.effect.kernel.Resource.ExitCase.Succeeded))
  }

  @Test
  def noopTracerProviderBuilderAndTracerPreserveNoopSemantics(): Unit = {
    val provider: TracerProvider[Id] = TracerProvider.noop[Id]
    val tracer: Tracer[Id] = provider
      .tracer("com.example.instrumentation")
      .withVersion("test-version")
      .withSchemaUrl("https://opentelemetry.io/schemas/test")
      .get

    val inputHeaders: Map[String, String] = Map("traceparent" -> "ignored")
    val propagated: Map[String, String] = tracer.propagate(inputHeaders)

    assertFalse(tracer.meta.isEnabled)
    assertEquals((), tracer.meta.unit)
    assertEquals(None, tracer.currentSpanContext)
    assertEquals(inputHeaders, propagated)
    assertEquals("root", tracer.rootScope("root"))
    assertEquals("noop", tracer.noopScope("noop"))
    assertEquals("child", tracer.childScope(SpanContext.invalid)("child"))
    assertEquals("continue", tracer.childOrContinue(None)("continue"))
    assertEquals("child-option", tracer.childOrContinue(Some(SpanContext.invalid))("child-option"))
    assertEquals("joined", tracer.joinOrRoot(inputHeaders)("joined"))
    assertEquals("direct", provider.get("direct").rootScope("direct"))
  }

  @Test
  def noopSpanBuilderAndSpanOperationsExerciseLifecycleAndMacroMethods(): Unit = {
    val tracer: Tracer[Id] = Tracer.noop[Id]
    val parent: SpanContext = SpanContext(
      SpanContext.TraceId.fromLongs(1L, 2L),
      SpanContext.SpanId.fromLong(3L),
      TraceFlags.Sampled,
      TraceState.empty.updated("vendor", "state"),
      remote = true
    )

    val span = tracer
      .spanBuilder("operation")
      .addAttribute(Attribute("single", "value"))
      .addAttributes(Attribute("many", 1L), Attribute("flag", true))
      .addLink(parent, Attribute("link", "parent"))
      .withFinalizationStrategy(SpanFinalizer.Strategy.reportAbnormal)
      .withSpanKind(SpanKind.Client)
      .withStartTimestamp(123.millis)
      .root
      .withParent(parent)
      .build
      .startUnmanaged

    assertEquals(SpanContext.invalid, span.context)
    assertFalse(span.backend.meta.isEnabled)
    assertEquals((), span.updateName("renamed"))
    assertEquals((), span.addAttribute(Attribute("span.attribute", "value")))
    assertEquals((), span.addAttributes(Attribute("span.count", 3L), Attribute("span.ok", true)))
    assertEquals((), span.addEvent("event", Attribute("event.attr", "value")))
    assertEquals((), span.addEvent("timed", 456.millis, Attribute("event.time", 456L)))
    assertEquals((), span.recordException(new RuntimeException("recorded"), Attribute("exception", true)))
    assertEquals((), span.setStatus(Status.Ok))
    assertEquals((), span.setStatus(Status.Error, "description"))
    assertEquals((), span.end)
    assertEquals((), span.end(789.millis))

    assertEquals("used", tracer.span("macro-span", Attribute("macro", "yes")).use(_ => "used"))
    assertEquals("surrounded", tracer.rootSpan("root-macro").surround("surrounded"))
    assertEquals((), tracer.spanBuilder("unit-span").build.use_)

    val currentOrNoop = tracer.currentSpanOrNoop
    assertEquals(SpanContext.invalid, currentOrNoop.context)
  }

  @Test
  def spanMapKTranslatesEffectsWhileDelegatingToOriginalBackend(): Unit = {
    val context: SpanContext = SpanContext(
      SpanContext.TraceId.fromLongs(0x1011121314151617L, 0x2021222324252627L),
      SpanContext.SpanId.fromLong(0x3031323334353637L),
      TraceFlags.Sampled,
      TraceState.empty.updated("vendor", "mapped"),
      remote = false
    )
    val recordingBackend = new RecordingSpanBackend(context)
    val span: Span[Id] = new Span[Id] {
      val backend: Span.Backend[Id] = recordingBackend
    }
    val mapped: Span[Option] = span.mapK(new FunctionK[Id, Option] {
      def apply[A](fa: Id[A]): Option[A] = Some(fa)
    })

    assertEquals(context, mapped.context)
    assertEquals(Some(()), mapped.updateName("mapped-operation"))
    assertEquals(Some(()), mapped.addAttribute(Attribute("mapped.single", "value")))
    assertEquals(Some(()), mapped.addEvent("mapped.event", Attribute("event.attr", 7L)))
    assertEquals(Some(()), mapped.setStatus(Status.Error, "mapped failure"))
    assertEquals(Some(()), mapped.end(250.millis))
    assertEquals(
      List(
        "updateName:mapped-operation",
        "addAttributes:1",
        "addEvent:mapped.event:1",
        "setStatusWithDescription:mapped failure",
        "endAt:250"
      ),
      recordingBackend.operations.toList
    )
  }

  @Test
  def enabledTracerMacrosCreateChildAndRootSpanOpsThroughBuilder(): Unit = {
    val childTracer = new RecordingTracer
    val childResult: String = childTracer
      .span("child-operation", Attribute("component", "macro"))
      .surround("child-result")

    assertEquals("child-result", childResult)
    assertEquals(
      List("spanBuilder:child-operation", "addAttributes", "build", "use"),
      childTracer.operations.toList
    )
    assertEquals(List(Attribute("component", "macro")), childTracer.lastBuilder.attributes.toList)

    val rootTracer = new RecordingTracer
    val rootResult: String = rootTracer
      .rootSpan("root-operation", Attribute("root", true))
      .use(_ => "root-result")

    assertEquals("root-result", rootResult)
    assertEquals(
      List("spanBuilder:root-operation", "root", "addAttributes", "build", "use"),
      rootTracer.operations.toList
    )
    assertEquals(List(Attribute("root", true)), rootTracer.lastBuilder.attributes.toList)
  }

  private final class RecordingTracer extends Tracer[Id] {
    val operations: ListBuffer[String] = ListBuffer.empty
    var lastBuilder: RecordingSpanBuilder = _

    val meta: Tracer.Meta[Id] = Tracer.Meta.enabled[Id]
    val currentSpanContext: Option[SpanContext] = None
    val currentSpanOrNoop: Span[Id] = new RecordingSpan

    def spanBuilder(name: String): SpanBuilder[Id] = {
      operations += s"spanBuilder:$name"
      lastBuilder = new RecordingSpanBuilder(operations)
      lastBuilder
    }

    def childScope[A](parent: SpanContext)(fa: Id[A]): Id[A] = fa

    def joinOrRoot[A, C](carrier: C)(fa: Id[A])(implicit
        getter: TextMapGetter[C]
    ): Id[A] = fa

    def rootScope[A](fa: Id[A]): Id[A] = fa

    def noopScope[A](fa: Id[A]): Id[A] = fa

    def propagate[C](carrier: C)(implicit updater: TextMapUpdater[C]): Id[C] = carrier
  }

  private final class RecordingSpanBuilder(operations: ListBuffer[String]) extends SpanBuilder[Id] {
    val attributes: ListBuffer[Attribute[_]] = ListBuffer.empty
    private val span: Span[Id] = new RecordingSpan

    def addAttribute[A](attribute: Attribute[A]): SpanBuilder[Id] = {
      operations += "addAttribute"
      attributes += attribute
      this
    }

    def addAttributes(attributes: Attribute[_]*): SpanBuilder[Id] = {
      operations += "addAttributes"
      this.attributes ++= attributes
      this
    }

    def addLink(spanContext: SpanContext, attributes: Attribute[_]*): SpanBuilder[Id] = {
      operations += "addLink"
      this
    }

    def withFinalizationStrategy(strategy: SpanFinalizer.Strategy): SpanBuilder[Id] = {
      operations += "withFinalizationStrategy"
      this
    }

    def withSpanKind(spanKind: SpanKind): SpanBuilder[Id] = {
      operations += "withSpanKind"
      this
    }

    def withStartTimestamp(timestamp: FiniteDuration): SpanBuilder[Id] = {
      operations += "withStartTimestamp"
      this
    }

    def root: SpanBuilder[Id] = {
      operations += "root"
      this
    }

    def withParent(parent: SpanContext): SpanBuilder[Id] = {
      operations += "withParent"
      this
    }

    def build: SpanOps[Id] = {
      operations += "build"
      new RecordingSpanOps(operations, span)
    }
  }

  private final class RecordingSpanOps(operations: ListBuffer[String], span: Span[Id]) extends SpanOps[Id] {
    def startUnmanaged: Span[Id] = {
      operations += "startUnmanaged"
      span
    }

    def resource: Resource[Id, SpanOps.Res[Id]] =
      Resource.pure(SpanOps.Res(span, FunctionK.id[Id]))

    def use[A](f: Span[Id] => Id[A]): Id[A] = {
      operations += "use"
      f(span)
    }

    override def use_ : Id[Unit] = {
      operations += "use_"
      ()
    }
  }

  private final class RecordingSpan extends Span[Id] {
    val backend: Span.Backend[Id] = Span.Backend.noop[Id]
  }

  private final class RecordingSpanBackend(override val context: SpanContext) extends Span.Backend[Id] {
    val operations: ListBuffer[String] = ListBuffer.empty
    val meta: InstrumentMeta[Id] = Tracer.Meta.enabled[Id]

    def updateName(name: String): Id[Unit] = {
      operations += s"updateName:$name"
      ()
    }

    def addAttributes(attributes: Attribute[_]*): Id[Unit] = {
      operations += s"addAttributes:${attributes.size}"
      ()
    }

    def addEvent(name: String, attributes: Attribute[_]*): Id[Unit] = {
      operations += s"addEvent:$name:${attributes.size}"
      ()
    }

    def addEvent(name: String, timestamp: FiniteDuration, attributes: Attribute[_]*): Id[Unit] = {
      operations += s"addEventAt:$name:${timestamp.toMillis}:${attributes.size}"
      ()
    }

    def recordException(throwable: Throwable, attributes: Attribute[_]*): Id[Unit] = {
      operations += s"recordException:${throwable.getClass.getSimpleName}:${attributes.size}"
      ()
    }

    def setStatus(status: Status): Id[Unit] = {
      operations += "setStatus"
      ()
    }

    def setStatus(status: Status, description: String): Id[Unit] = {
      operations += s"setStatusWithDescription:$description"
      ()
    }

    def end: Id[Unit] = {
      operations += "end"
      ()
    }

    def end(timestamp: FiniteDuration): Id[Unit] = {
      operations += s"endAt:${timestamp.toMillis}"
      ()
    }
  }
}
