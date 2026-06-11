/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatestplus.mockito_5_12_3

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.MockSettings
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatestplus.mockito.MockitoSugar

class Mockito_5_12_3Test {
  @Test
  def companionObjectMockCreatesStubbableMock(): Unit = {
    withMockitoRuntime {
      val formatter: Function[String, String] = MockitoSugar.mock[Function[String, String]]

      Mockito.when(formatter.apply("Ada")).thenReturn("Hello Ada")

      assertThat(formatter.apply("Ada")).isEqualTo("Hello Ada")
      assertThat(Mockito.mockingDetails(formatter).isMock).isTrue()
      Mockito.verify(formatter).apply("Ada")
    }
  }

  @Test
  def companionObjectMockSupportsScalaTraits(): Unit = {
    withMockitoRuntime {
      val greeter: GreetingApi = MockitoSugar.mock[GreetingApi]

      Mockito.when(greeter.greeting("Scala")).thenReturn("Mocked greeting")

      assertThat(greeter.greeting("Scala")).isEqualTo("Mocked greeting")
      assertThat(Mockito.mockingDetails(greeter).isMock).isTrue()
      Mockito.verify(greeter).greeting("Scala")
    }
  }

  @Test
  def importedCompanionMembersCreateMocksWithoutMixingInTheTrait(): Unit = {
    withMockitoRuntime {
      import org.scalatestplus.mockito.MockitoSugar.mock

      val supplier: Supplier[String] = mock[Supplier[String]]

      Mockito.when(supplier.get()).thenReturn("ready")

      assertThat(supplier.get()).isEqualTo("ready")
      Mockito.verify(supplier).get()
    }
  }

  @Test
  def traitMixinSupportsNamedMockOverload(): Unit = {
    withMockitoRuntime {
      val supplier: Supplier[String] = MixedInSugar.mock[Supplier[String]]("namedSupplier")

      Mockito.when(supplier.get()).thenReturn("named value")

      assertThat(supplier.get()).isEqualTo("named value")
      assertThat(Mockito.mockingDetails(supplier).getMockCreationSettings.getMockName.toString)
        .isEqualTo("namedSupplier")
    }
  }

  @Test
  def defaultAnswerOverloadProvidesFallbackBehavior(): Unit = {
    withMockitoRuntime {
      val defaultAnswer: Answer[AnyRef] = new Answer[AnyRef] {
        override def answer(invocation: InvocationOnMock): AnyRef = {
          s"${invocation.getMethod.getName}:${invocation.getArgument[AnyRef](0)}"
        }
      }
      val formatter: Function[String, String] = MockitoSugar.mock[Function[String, String]](defaultAnswer)

      assertThat(formatter.apply("Grace")).isEqualTo("apply:Grace")
      Mockito.verify(formatter).apply("Grace")
    }
  }

  @Test
  def mockSettingsOverloadAppliesMockitoSettings(): Unit = {
    withMockitoRuntime {
      val settings: MockSettings = Mockito.withSettings()
        .name("settingsFunction")
        .defaultAnswer(Mockito.RETURNS_SMART_NULLS)
      val formatter: Function[String, String] = MockitoSugar.mock[Function[String, String]](settings)

      Mockito.when(formatter.apply("configured")).thenReturn("settings applied")

      assertThat(formatter.apply("configured")).isEqualTo("settings applied")
      assertThat(Mockito.mockingDetails(formatter).getMockCreationSettings.getMockName.toString)
        .isEqualTo("settingsFunction")
      Mockito.verify(formatter).apply("configured")
    }
  }

  @Test
  def captureCreatesArgumentCaptorForRequestedType(): Unit = {
    val captor: ArgumentCaptor[String] = MockitoSugar.capture[String]

    assertThat(captor.getAllValues).isEmpty()
  }

  @Test
  def captureCreatesArgumentCaptorAndImplicitlyCapturesVerificationArgument(): Unit = {
    withMockitoRuntime {
      import org.scalatestplus.mockito.MockitoSugar.*

      val consumer: Consumer[String] = MockitoSugar.mock[Consumer[String]]
      val captor: ArgumentCaptor[String] = capture[String]

      consumer.accept("captured value")
      Mockito.verify(consumer).accept(captor)

      assertThat(captor.getValue).isEqualTo("captured value")
      assertThat(captor.getAllValues).containsExactly("captured value")
    }
  }

  private def withMockitoRuntime(testBody: => Unit): Unit = {
    try {
      testBody
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }

  private object MixedInSugar extends MockitoSugar
}

trait GreetingApi {
  def greeting(name: String): String
}
