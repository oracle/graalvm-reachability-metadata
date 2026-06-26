/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatestplus.mockito_5_12_3

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

import scala.jdk.CollectionConverters.*

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.when
import org.mockito.Mockito.withSettings
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatestplus.mockito.MockitoSugar

class MockitoSugarTest extends MockitoSugar:
  @Test
  def mockCreatesAStubbedScalaTraitMock(): Unit =
    val catalog: Catalog = mock[Catalog]

    when(catalog.title("scala")).thenReturn("Scala Guide")
    when(catalog.available("scala")).thenReturn(true)

    assert(catalog.title("scala") == "Scala Guide")
    assert(catalog.available("scala"))
    verify(catalog).title("scala")
    verify(catalog).available("scala")
    verifyNoMoreInteractions(catalog)

  @Test
  def mockWithNameAssignsTheMockitoMockNameToAJavaInterfaceMock(): Unit =
    val audit: Consumer[String] = mock[Consumer[String]]("audit-consumer")

    audit.accept("created:42")

    verify(audit).accept("created:42")
    assert(mockingDetails(audit).getMockCreationSettings.getMockName.toString == "audit-consumer")

  @Test
  def mockWithDefaultAnswerUsesTheSuppliedAnswerForUnstubbedCalls(): Unit =
    val answer: Answer[AnyRef] = new Answer[AnyRef]:
      override def answer(invocation: InvocationOnMock): AnyRef =
        val argument: String = invocation.getArgument[String](0)
        s"${invocation.getMethod.getName}:$argument"

    val formatter: Function[String, String] = mock[Function[String, String]](answer)

    assert(formatter.apply("native") == "apply:native")
    assert(formatter.apply("jvm") == "apply:jvm")
    verify(formatter).apply("native")
    verify(formatter).apply("jvm")

  @Test
  def mockWithSettingsAppliesNameAndDefaultAnswer(): Unit =
    val answer: Answer[AnyRef] = new Answer[AnyRef]:
      override def answer(invocation: InvocationOnMock): AnyRef =
        val argument: String = invocation.getArgument[String](0)
        java.lang.Boolean.valueOf(argument.nonEmpty)

    val predicate: Predicate[String] = mock[Predicate[String]](
      withSettings().name("non-empty-predicate").defaultAnswer(answer)
    )

    assert(predicate.test("value"))
    assert(!predicate.test(""))
    assert(mockingDetails(predicate).getMockCreationSettings.getMockName.toString == "non-empty-predicate")
    verify(predicate).test("value")
    verify(predicate).test("")

  @Test
  def captureCreatesAnArgumentCaptorForVerifiedInterfaceCalls(): Unit =
    val sink: Consumer[String] = mock[Consumer[String]]
    val capturedPayload = capture[String]

    sink.accept("payload-1")
    sink.accept("payload-2")

    verify(sink, times(2)).accept(capturedPayload.capture())
    assert(capturedPayload.getAllValues.asScala.toVector == Vector("payload-1", "payload-2"))

  @Test
  def companionObjectMembersCanBeImportedWithoutMixingInTheTrait(): Unit =
    val lookup: Function[String, String] = MockitoSugar.mock[Function[String, String]]
    when(lookup.apply("key")).thenReturn("value")

    assert(lookup.apply("key") == "value")
    verify(lookup).apply("key")

trait Catalog:
  def title(id: String): String

  def available(id: String): Boolean
