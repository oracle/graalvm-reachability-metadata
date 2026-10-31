/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PackagingDataCalculatorTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void enrichesExceptionFramesWithAndWithoutAContextClassLoader(boolean useBootstrapLoader) {
    Thread thread = Thread.currentThread();
    ClassLoader originalLoader = thread.getContextClassLoader();
    try {
      if (useBootstrapLoader) {
        thread.setContextClassLoader(null);
      }
      NumberFormatException failure = assertThrows(NumberFormatException.class,
          () -> Integer.parseInt("not-a-number"));
      ThrowableProxy proxy = new ThrowableProxy(failure);
      proxy.calculatePackagingData();

      assertThat(proxy.getMessage()).contains("not-a-number");
      assertThat(proxy.getStackTraceElementProxyArray()).isNotEmpty().allSatisfy(frame -> {
        assertThat(frame.getClassPackagingData()).isNotNull();
        assertThat(frame.getClassPackagingData().getCodeLocation()).isNotBlank();
        assertThat(frame.getClassPackagingData().getVersion()).isNotBlank();
      });
      assertThat(proxy.getStackTraceElementProxyArray()).anySatisfy(
          PackagingDataCalculatorTest::assertIntegerFramePackaging);
    } finally {
      thread.setContextClassLoader(originalLoader);
    }
  }

  private static void assertIntegerFramePackaging(StackTraceElementProxy frame) {
    assertThat(frame.getStackTraceElement().getClassName()).isEqualTo("java.lang.Integer");
    assertThat(frame.getClassPackagingData().getVersion()).isEqualTo("na");
  }
}
