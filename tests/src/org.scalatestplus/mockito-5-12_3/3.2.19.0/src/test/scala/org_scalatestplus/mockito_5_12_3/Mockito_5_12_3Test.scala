/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatestplus.mockito_5_12_3

import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.scalatestplus.mockito.MockitoSugar

import scala.language.implicitConversions

class Mockito_5_12_3Test extends MockitoSugar {
  @Test
  def companionObjectCreatesTypedMocksWithoutClassLiterals(): Unit = exerciseMockitoDynamicClassGeneration {
    val runnable: Runnable = MockitoSugar.mock[Runnable]

    runnable.run()

    Mockito.verify(runnable).run()
    Mockito.verifyNoMoreInteractions(runnable)
    assertTrue(Mockito.mockingDetails(runnable).isMock)
  }

  @Test
  def traitProvidesTypedMocksThatCanBeStubbedAndVerified(): Unit = exerciseMockitoDynamicClassGeneration {
    val repository: LookupRepositoryForMockito_5_12_3Test = mock[LookupRepositoryForMockito_5_12_3Test]

    Mockito.when(repository.lookup("alpha")).thenReturn("value-alpha")

    assertEquals("value-alpha", repository.lookup("alpha"))
    Mockito.verify(repository).lookup("alpha")
    Mockito.verifyNoMoreInteractions(repository)
  }

  @Test
  def typedMocksCanTargetConcreteScalaClasses(): Unit = exerciseMockitoDynamicClassGeneration {
    val renderer: ReportRendererForMockito_5_12_3Test = mock[ReportRendererForMockito_5_12_3Test]

    Mockito.when(renderer.render("daily")).thenReturn("mock-daily-report")

    assertEquals("mock-daily-report", renderer.render("daily"))
    Mockito.verify(renderer).render("daily")
    Mockito.verifyNoMoreInteractions(renderer)
  }

  @Test
  def defaultAnswerOverloadControlsUnstubbedMethodResults(): Unit = exerciseMockitoDynamicClassGeneration {
    val answer: Answer[AnyRef] = invocation => {
      val key: String = invocation.getArgument[String](0)
      s"default-$key"
    }
    val repository: LookupRepositoryForMockito_5_12_3Test = mock[LookupRepositoryForMockito_5_12_3Test](answer)

    assertEquals("default-missing", repository.lookup("missing"))
    Mockito.when(repository.lookup("configured")).thenReturn("explicit-value")
    assertEquals("explicit-value", repository.lookup("configured"))
    assertEquals("default-other", repository.lookup("other"))

    Mockito.verify(repository).lookup("missing")
    Mockito.verify(repository).lookup("configured")
    Mockito.verify(repository).lookup("other")
  }

  @Test
  def namedMockOverloadAppliesMockitoMockName(): Unit = exerciseMockitoDynamicClassGeneration {
    val repository: LookupRepositoryForMockito_5_12_3Test = mock[LookupRepositoryForMockito_5_12_3Test]("namedRepository")

    val mockName: String = Mockito.mockingDetails(repository).getMockCreationSettings.getMockName.toString

    assertEquals("namedRepository", mockName)
    Mockito.when(repository.lookup("id-1")).thenReturn("named-result")
    assertEquals("named-result", repository.lookup("id-1"))
    Mockito.verify(repository).lookup("id-1")
  }

  @Test
  def mockSettingsOverloadSupportsDeepStubsNamesAndExtraInterfaces(): Unit = exerciseMockitoDynamicClassGeneration {
    val service: NestedServiceForMockito_5_12_3Test = mock[NestedServiceForMockito_5_12_3Test](
      Mockito
        .withSettings()
        .name("nestedService")
        .defaultAnswer(Answers.RETURNS_DEEP_STUBS)
        .extraInterfaces(classOf[ResettableForMockito_5_12_3Test])
    )

    Mockito.when(service.repository().lookup("deep-id")).thenReturn("deep-value")

    assertEquals("nestedService", Mockito.mockingDetails(service).getMockCreationSettings.getMockName.toString)
    assertEquals("deep-value", service.repository().lookup("deep-id"))
    assertTrue(service.isInstanceOf[ResettableForMockito_5_12_3Test])

    val resettable: ResettableForMockito_5_12_3Test = service.asInstanceOf[ResettableForMockito_5_12_3Test]
    resettable.resetAll()
    Mockito.verify(resettable).resetAll()
  }

  @Test
  def captureCreatesTypedArgumentCaptorsAndImplicitlyCapturesDuringVerification(): Unit =
    exerciseMockitoDynamicClassGeneration {
      val sink: AuditSinkForMockito_5_12_3Test = mock[AuditSinkForMockito_5_12_3Test]
      val firstEvent: AuditEventForMockito_5_12_3Test = AuditEventForMockito_5_12_3Test("created", 1)
      val secondEvent: AuditEventForMockito_5_12_3Test = AuditEventForMockito_5_12_3Test("updated", 2)
      val captor: ArgumentCaptor[AuditEventForMockito_5_12_3Test] = capture[AuditEventForMockito_5_12_3Test]

      sink.publish(firstEvent)
      sink.publish(secondEvent)

      Mockito.verify(sink, Mockito.times(2)).publish(captor)
      assertNotNull(captor.getValue)
      assertEquals(2, captor.getAllValues.size())
      assertEquals(firstEvent, captor.getAllValues.get(0))
      assertEquals(secondEvent, captor.getAllValues.get(1))
      Mockito.verifyNoMoreInteractions(sink)
    }

  @Test
  def companionObjectMembersCanBeImportedWithoutMixingInTrait(): Unit = exerciseMockitoDynamicClassGeneration {
    val repository: LookupRepositoryForMockito_5_12_3Test =
      new ImportedMockitoSugarFactoryForMockito_5_12_3Test().createRepository()

    Mockito.when(repository.lookup("imported-id")).thenReturn("imported-result")

    assertEquals("imported-result", repository.lookup("imported-id"))
    Mockito.verify(repository).lookup("imported-id")
    Mockito.verifyNoMoreInteractions(repository)
  }

  private def exerciseMockitoDynamicClassGeneration(body: => Unit): Unit = {
    try {
      body
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }
}

trait LookupRepositoryForMockito_5_12_3Test {
  def lookup(key: String): String
}

class ReportRendererForMockito_5_12_3Test {
  def render(reportName: String): String = s"real-$reportName"
}

trait NestedServiceForMockito_5_12_3Test {
  def repository(): LookupRepositoryForMockito_5_12_3Test
}

trait ResettableForMockito_5_12_3Test {
  def resetAll(): Unit
}

trait AuditSinkForMockito_5_12_3Test {
  def publish(event: AuditEventForMockito_5_12_3Test): Unit
}

final class ImportedMockitoSugarFactoryForMockito_5_12_3Test {
  def createRepository(): LookupRepositoryForMockito_5_12_3Test = {
    import org.scalatestplus.mockito.MockitoSugar.mock

    mock[LookupRepositoryForMockito_5_12_3Test]
  }
}

final case class AuditEventForMockito_5_12_3Test(name: String, revision: Int)
