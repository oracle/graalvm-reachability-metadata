/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.PackagingDataCalculator;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

public class PackagingDataCalculatorTest {

    @Test
    void fallsBackToClassForNameWhenTheContextClassLoaderRejectsTheThrowingFrame() {
        ThrowableProxy throwableProxy = new ThrowableProxy(new IllegalStateException("boom"));
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new RejectingClassLoader(previousClassLoader, getClass().getName()));

        try {
            new PackagingDataCalculator().calculate(throwableProxy);
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }

        assertThat(throwableProxy.getStackTraceElementProxyArray()).isNotEmpty();
        assertThat(throwableProxy.getStackTraceElementProxyArray()[0].getClassPackagingData()).isNotNull();
        assertThat(throwableProxy.getStackTraceElementProxyArray()[0].getClassPackagingData().getCodeLocation())
                .isNotBlank();
    }

    private static final class RejectingClassLoader extends ClassLoader {

        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
