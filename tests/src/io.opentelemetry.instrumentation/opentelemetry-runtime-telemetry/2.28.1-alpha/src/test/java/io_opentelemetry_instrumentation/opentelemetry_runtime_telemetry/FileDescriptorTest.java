/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_runtime_telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.FileDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FileDescriptorTest {
    @Test
    void registerObserversInitializesUnixMxBeanSupport() throws Exception {
        Meter meter = OpenTelemetry.noop().getMeter("file-descriptor-test");

        List<AutoCloseable> observables = FileDescriptor.registerObservers(meter);

        assertThat(observables)
                .as("file descriptor observers are optional on non-Unix operating systems")
                .isNotNull();
        for (AutoCloseable observable : observables) {
            observable.close();
        }
    }
}
