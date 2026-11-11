/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.PackagingDataCalculator;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PackagingDataCalculatorTest {

    @Test
    void resolvesStackFramesUsingTheContextClassLoader() {
        assertLibraryFramePackagingData();
    }

    @Test
    void resolvesStackFramesWithoutAContextClassLoader() {
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        try {
            current.setContextClassLoader(null);
            assertLibraryFramePackagingData();
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    private static void assertLibraryFramePackagingData() {
        LoggerContext context = new LoggerContext();
        try {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> context.getLogger((String) null));
            ThrowableProxy proxy = new ThrowableProxy(exception);
            new PackagingDataCalculator().calculate(proxy);

            assertThat(proxy.getStackTraceElementProxyArray()[0].getStackTraceElement().getClassName())
                    .isEqualTo(LoggerContext.class.getName());
            ClassPackagingData packaging = proxy.getStackTraceElementProxyArray()[0].getClassPackagingData();
            assertThat(packaging).isNotNull();
            String version = LoggerContext.class.getPackage().getImplementationVersion();
            assertThat(packaging.getVersion()).isEqualTo(version == null ? "na" : version);
            assertThat(packaging.getCodeLocation()).isNotBlank();
        } finally {
            context.stop();
        }
    }
}
