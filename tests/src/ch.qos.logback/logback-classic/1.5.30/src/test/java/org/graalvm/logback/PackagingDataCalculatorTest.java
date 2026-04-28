/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PackagingDataCalculatorTest {

  @Test
  void calculatesPackagingDataWithContextClassLoader() {
    ThrowableProxy throwableProxy = throwableProxyWithFrame(PackagingDataCalculatorTest.class.getName());

    throwableProxy.calculatePackagingData();

    ClassPackagingData packagingData = firstPackagingData(throwableProxy);
    assertThat(packagingData).isNotNull();
    assertThat(packagingData.isExact()).isFalse();
  }

  @Test
  void fallsBackWhenContextClassLoaderCannotLoadStackTraceClass() {
    String stackTraceClassName = String.class.getName();
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(originalClassLoader, stackTraceClassName);
    ThrowableProxy throwableProxy = throwableProxyWithFrame(stackTraceClassName);

    try {
      Thread.currentThread().setContextClassLoader(rejectingClassLoader);
      throwableProxy.calculatePackagingData();
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    ClassPackagingData packagingData = firstPackagingData(throwableProxy);
    assertThat(rejectingClassLoader.wasAskedForRejectedClass()).isTrue();
    assertThat(packagingData).isNotNull();
    assertThat(packagingData.isExact()).isFalse();
  }

  private static ThrowableProxy throwableProxyWithFrame(String className) {
    RuntimeException exception = new RuntimeException("packaging data test");
    exception.setStackTrace(new StackTraceElement[] {
        new StackTraceElement(className, "method", "PackagingDataCalculatorTest.java", 1)
    });
    return new ThrowableProxy(exception);
  }

  private static ClassPackagingData firstPackagingData(ThrowableProxy throwableProxy) {
    StackTraceElementProxy firstFrame = throwableProxy.getStackTraceElementProxyArray()[0];
    return firstFrame.getClassPackagingData();
  }

  private static final class RejectingClassLoader extends ClassLoader {

    private final ClassLoader delegate;
    private final String rejectedClassName;
    private boolean askedForRejectedClass;

    private RejectingClassLoader(ClassLoader delegate, String rejectedClassName) {
      super(null);
      this.delegate = delegate;
      this.rejectedClassName = rejectedClassName;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
      if (rejectedClassName.equals(name)) {
        askedForRejectedClass = true;
        throw new ClassNotFoundException(name);
      }
      if (delegate != null) {
        return delegate.loadClass(name);
      }
      return super.loadClass(name);
    }

    private boolean wasAskedForRejectedClass() {
      return askedForRejectedClass;
    }
  }
}
