/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.PackagingDataCalculator;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class PackagingDataCalculatorTest {

  private static final String LOADABLE_CLASS_NAME = PackagingDataCalculator.class.getName();

  @Test
  void calculatesPackagingDataUsingContextClassLoader() {
    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    ThrowableProxy proxy = createProxyFor(LOADABLE_CLASS_NAME);

    try {
      Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
      proxy.calculatePackagingData();
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    ClassPackagingData packagingData = firstPackagingData(proxy);
    assertThat(packagingData).isNotNull();
    assertThat(packagingData.isExact()).isFalse();
  }

  @Test
  void calculatesPackagingDataUsingClassForNameWhenContextClassLoaderIsMissing() {
    ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    ThrowableProxy proxy = createProxyFor(LOADABLE_CLASS_NAME);

    try {
      Thread.currentThread().setContextClassLoader(null);
      proxy.calculatePackagingData();
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    ClassPackagingData packagingData = firstPackagingData(proxy);
    assertThat(packagingData).isNotNull();
    assertThat(packagingData.isExact()).isFalse();
  }

  private static ThrowableProxy createProxyFor(String className) {
    RuntimeException exception = new RuntimeException("packaging data test");
    exception.setStackTrace(new StackTraceElement[] {
        new StackTraceElement(className, "calculate", "PackagingDataCalculator.java", 1)
    });
    return new ThrowableProxy(exception);
  }

  private static ClassPackagingData firstPackagingData(ThrowableProxy proxy) {
    StackTraceElementProxy[] stackTraceElementProxies = proxy.getStackTraceElementProxyArray();
    assertThat(stackTraceElementProxies).hasSize(1);
    return stackTraceElementProxies[0].getClassPackagingData();
  }
}
