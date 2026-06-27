/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_stacktracer_2_13

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

class Zio_stacktracer_2_13Test {
  @Test
  def sourceLocationMacroCapturesTheCompilationSite(): Unit = {
    val location: SourceLocation = capturedSourceLocation()

    assertTrue(
      location.path.endsWith("Zio_stacktracer_2_13Test.scala"),
      s"Unexpected path: ${location.path}")
    assertTrue(location.line > 0, s"Unexpected line: ${location.line}")

    val copied: SourceLocation = location.copy(
      path = location.path + ".copy",
      line = location.line + 1)
    assertEquals(SourceLocation(location.path + ".copy", location.line + 1), copied)
    assertEquals(List("path", "line"), location.productElementNames.toList)
  }

  @Test
  def sourceLocationCompanionSupportsCaseClassOperations(): Unit = {
    val location: SourceLocation = SourceLocation("src/main/scala/Example.scala", 17)
    val SourceLocation(path, line) = location

    assertEquals("src/main/scala/Example.scala", path)
    assertEquals(17, line)
    assertEquals("SourceLocation", location.productPrefix)
    assertEquals(2, location.productArity)
    assertEquals("src/main/scala/Example.scala", location.productElement(0))
    assertEquals(17, location.productElement(1))
    assertEquals(List("src/main/scala/Example.scala", 17), location.productIterator.toList)
  }

  @Test
  def explicitNewTraceContainsTheCallingTestMethod(): Unit = {
    val trace: Tracer.instance.Type = Tracer.newTrace
    val decoded: Option[(String, String, Int)] = decode(trace)

    assertTrue(decoded.isDefined, s"Trace could not be decoded: $trace")
    val (location, file, line): (String, String, Int) = decoded.get
    assertTrue(
      location.contains("Zio_stacktracer_2_13Test"),
      s"Unexpected trace location: $location")
    assertTrue(
      location.contains("explicitNewTraceContainsTheCallingTestMethod"),
      s"Unexpected trace location: $location")
    assertEquals("Zio_stacktracer_2_13Test.scala", file)
    assertTrue(line > 0, s"Unexpected trace line: $line")
  }

  @Test
  def automaticTraceIsSynthesizedWhenImplicitTraceIsRequired(): Unit = {
    val decoded: Option[(String, String, Int)] = decode(automaticallyCapturedTrace())

    assertTrue(decoded.isDefined, "Implicit trace was not synthesized")
    val (location, file, line): (String, String, Int) = decoded.get
    assertTrue(
      location.contains("Zio_stacktracer_2_13Test"),
      s"Unexpected trace location: $location")
    assertTrue(
      location.contains("automaticTraceIsSynthesizedWhenImplicitTraceIsRequired"),
      s"Unexpected trace location: $location")
    assertEquals("Zio_stacktracer_2_13Test.scala", file)
    assertTrue(line > 0, s"Unexpected trace line: $line")
  }

  @Test
  def explicitImplicitTraceTakesPrecedenceOverAutomaticTrace(): Unit = {
    implicit val manualTrace: Tracer.instance.Type =
      Tracer.instance("manual.location", "Manual.scala", 42)

    assertEquals(
      Some(("manual.location", "Manual.scala", 42)),
      decode(automaticallyCapturedTrace()))
  }

  @Test
  def tracerInstanceRoundTripsLocationFileAndLine(): Unit = {
    val trace: Tracer.instance.Type =
      Tracer.instance("example.service.operation", "ExampleService.scala", 12345)
    val decoded: Option[(String, String, Int)] = decode(trace)

    assertEquals(Some(("example.service.operation", "ExampleService.scala", 12345)), decoded)
    assertSame(
      trace.asInstanceOf[AnyRef],
      Tracer
        .instance("example.service.operation", "ExampleService.scala", 12345)
        .asInstanceOf[AnyRef])
    assertSame(Tracer.instance, Tracer.instance)
  }

  @Test
  def manuallyCreatedTraceRoundTripsAndIsInterned(): Unit = {
    val trace: Tracer.instance.Type =
      Tracer.instance("example.Workflow.resume", "Workflow.scala", 27)

    assertEquals(Some(("example.Workflow.resume", "Workflow.scala", 27)), decode(trace))
    assertSame(
      trace.asInstanceOf[AnyRef],
      Tracer.instance("example.Workflow.resume", "Workflow.scala", 27).asInstanceOf[AnyRef])
  }

  @Test
  def emptyTraceDoesNotDecodeAndIsInterned(): Unit = {
    val trace: Tracer.instance.Type = Tracer.instance.empty

    assertEquals(None, decode(trace))
    assertSame(trace.asInstanceOf[AnyRef], Tracer.instance.empty.asInstanceOf[AnyRef])
  }

  @Test
  def explicitNewTraceCanBeForcedWhenAutomaticTracingIsDisabled(): Unit = {
    implicit val disableAutoTrace: DisableAutoTrace = TracingImplicits.disableAutoTrace

    val trace: Tracer.instance.Type = Tracer.newTrace
    val decoded: Option[(String, String, Int)] = decode(trace)

    assertTrue(decoded.isDefined, s"Trace could not be decoded: $trace")
    val (location, file, line): (String, String, Int) = decoded.get
    assertTrue(
      location.contains("Zio_stacktracer_2_13Test"),
      s"Unexpected trace location: $location")
    assertTrue(
      location.contains("explicitNewTraceCanBeForcedWhenAutomaticTracingIsDisabled"),
      s"Unexpected trace location: $location")
    assertEquals("Zio_stacktracer_2_13Test.scala", file)
    assertTrue(line > 0, s"Unexpected trace line: $line")
  }

  @Test
  def tracingImplicitsExposeASingleDisableAutoTraceMarker(): Unit = {
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
    assertEquals("BuildInfo", BuildInfo.productPrefix)
    assertEquals(0, BuildInfo.productArity)
    assertTrue(BuildInfo.productIterator.isEmpty)
    assertTrue(BuildInfo.toString.contains("organization: dev.zio"))
  }

  private def capturedSourceLocation(): SourceLocation = implicitly[SourceLocation]

  private def automaticallyCapturedTrace()(implicit
      trace: Tracer.instance.Type): Tracer.instance.Type = trace

  private def decode(trace: Tracer.instance.Type): Option[(String, String, Int)] =
    Tracer.instance.unapply(trace)
}
