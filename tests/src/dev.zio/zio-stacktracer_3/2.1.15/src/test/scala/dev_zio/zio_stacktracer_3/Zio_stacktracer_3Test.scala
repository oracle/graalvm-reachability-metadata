/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_stacktracer_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zio.internal.stacktracer.BuildInfo
import zio.internal.stacktracer.SourceLocation
import zio.internal.stacktracer.Tracer
import zio.stacktracer.DisableAutoTrace
import zio.stacktracer.TracingImplicits

class Zio_stacktracer_3Test {
  @Test
  def sourceLocationMacroCapturesTheCompilationSite(): Unit = {
    val location: SourceLocation = capturedSourceLocation()

    assertTrue(location.path.endsWith("Zio_stacktracer_3Test.scala"), s"Unexpected path: ${location.path}")
    assertTrue(location.line > 0, s"Unexpected line: ${location.line}")

    val copied: SourceLocation = location.copy(path = location.path + ".copy", line = location.line + 1)
    assertEquals(SourceLocation(location.path + ".copy", location.line + 1), copied)
    assertEquals(List("path", "line"), location.productElementNames.toList)
  }

  @Test
  def explicitNewTraceContainsTheCallingTestMethod(): Unit = {
    val trace: Tracer.instance.Type = Tracer.newTrace
    val decoded: Option[(String, String, Int)] = decode(trace)

    assertTrue(decoded.isDefined, s"Trace could not be decoded: $trace")
    val (location, file, line): (String, String, Int) = decoded.get
    assertTrue(location.contains("Zio_stacktracer_3Test"), s"Unexpected trace location: $location")
    assertTrue(location.contains("explicitNewTraceContainsTheCallingTestMethod"), s"Unexpected trace location: $location")
    assertEquals("Zio_stacktracer_3Test.scala", file)
    assertTrue(line > 0, s"Unexpected trace line: $line")
  }

  @Test
  def automaticTraceIsSynthesizedWhenImplicitTraceIsRequired(): Unit = {
    val decoded: Option[(String, String, Int)] = decode(automaticallyCapturedTrace())

    assertTrue(decoded.isDefined)
    val (location, file, line): (String, String, Int) = decoded.get
    assertTrue(location.contains("Zio_stacktracer_3Test"), s"Unexpected trace location: $location")
    assertTrue(location.contains("automaticTraceIsSynthesizedWhenImplicitTraceIsRequired"), s"Unexpected trace location: $location")
    assertEquals("Zio_stacktracer_3Test.scala", file)
    assertTrue(line > 0, s"Unexpected trace line: $line")
  }

  @Test
  def explicitImplicitTraceTakesPrecedenceOverAutomaticTrace(): Unit = {
    val manualTrace: Tracer.instance.Type = Tracer.instance("manual.location", "Manual.scala", 42)
    val decoded: Option[(String, String, Int)] = decode(automaticallyCapturedTrace()(using manualTrace))

    assertEquals(Some(("manual.location", "Manual.scala", 42)), decoded)
  }

  @Test
  def tracerInstanceRoundTripsLocationFileAndLine(): Unit = {
    val trace: Tracer.instance.Type = Tracer.instance("example.service.operation", "ExampleService.scala", 12345)
    val decoded: Option[(String, String, Int)] = decode(trace)

    assertEquals(Some(("example.service.operation", "ExampleService.scala", 12345)), decoded)
    assertSame(trace.asInstanceOf[AnyRef], Tracer.instance("example.service.operation", "ExampleService.scala", 12345).asInstanceOf[AnyRef])
  }

  @Test
  def manuallyCreatedTraceCarriesTheTracedMarkerAndSupportsParenthesizedLocations(): Unit = {
    val location: String = "example.Workflow(step).resume"
    val file: String = "Workflow.scala"
    val line: Int = 27
    val trace: Tracer.instance.Type & Tracer.Traced = Tracer.instance(location, file, line)
    val traced: Tracer.Traced = requireTraced(trace)

    assertSame(trace.asInstanceOf[AnyRef], traced.asInstanceOf[AnyRef])
    assertEquals(Some((location, file, line)), decode(trace))
  }

  @Test
  def explicitNewTraceCanBeForcedWhenAutomaticTracingIsDisabled(): Unit = {
    given DisableAutoTrace = TracingImplicits.disableAutoTrace

    val trace: Tracer.instance.Type = Tracer.newTrace
    val decoded: Option[(String, String, Int)] = decode(trace)

    assertTrue(decoded.isDefined, s"Trace could not be decoded: $trace")
    val (location, file, line): (String, String, Int) = decoded.get
    assertTrue(location.contains("Zio_stacktracer_3Test"), s"Unexpected trace location: $location")
    assertTrue(location.contains("explicitNewTraceCanBeForcedWhenAutomaticTracingIsDisabled"), s"Unexpected trace location: $location")
    assertEquals("Zio_stacktracer_3Test.scala", file)
    assertTrue(line > 0, s"Unexpected trace line: $line")
  }

  @Test
  def tracerRejectsEmptyAndMalformedTraces(): Unit = {
    val invalidTraces: List[Tracer.instance.Type] = List(
      Tracer.instance.empty,
      unsafeTrace("not-a-trace"),
      unsafeTrace("location(file.scala:0)"),
      unsafeTrace("location(file.scala:-1)"),
      unsafeTrace("location(file.scala:abc)"),
      unsafeTrace("location(file.scala:2147483648)"),
      unsafeTrace("location(file.scala:1"),
      unsafeTrace("locationfile.scala:1)")
    )

    invalidTraces.foreach { trace =>
      assertEquals(None, decode(trace), s"Malformed trace should not decode: $trace")
    }
  }

  @Test
  def tracingImplicitsExposeADisableAutoTraceMarker(): Unit = {
    val first: DisableAutoTrace = TracingImplicits.disableAutoTrace
    val second: DisableAutoTrace = TracingImplicits.disableAutoTrace

    assertNotNull(first)
    assertSame(first, second)
  }

  @Test
  def buildInfoExposesStableModuleMetadata(): Unit = {
    assertEquals("dev.zio", BuildInfo.organization)
    assertEquals("zio-stacktracer", BuildInfo.moduleName)
    assertEquals("zio-stacktracer", BuildInfo.name)
    assertFalse(BuildInfo.version.isBlank)
    assertFalse(BuildInfo.scalaVersion.isBlank)
    assertFalse(BuildInfo.sbtVersion.isBlank)
    assertFalse(BuildInfo.isSnapshot)
    assertTrue(BuildInfo.optimizationsEnabled)
    assertTrue(BuildInfo.toString.contains("organization: dev.zio"))
  }

  private def capturedSourceLocation(): SourceLocation = summon[SourceLocation]

  private def automaticallyCapturedTrace()(using trace: Tracer.instance.Type): Tracer.instance.Type = trace

  private def decode(trace: Tracer.instance.Type): Option[(String, String, Int)] = Tracer.instance.unapply(trace)

  private def requireTraced(trace: Tracer.Traced): Tracer.Traced = trace

  private def unsafeTrace(trace: String): Tracer.instance.Type = trace.asInstanceOf[Tracer.instance.Type]
}
