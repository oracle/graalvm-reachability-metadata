/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_runtime_telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.runtimetelemetry.internal.CpuMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class CpuMethodsTest {
    @Test
    void cpuSuppliersInvokeOperatingSystemManagementBeanMethods() {
        List<Number> measurements = new ArrayList<>();

        Supplier<Long> processCpuTime = CpuMethods.processCpuTime();
        collectMeasurement(measurements, processCpuTime);
        collectMeasurement(measurements, CpuMethods.processCpuUtilization());
        collectMeasurement(measurements, CpuMethods.systemCpuUtilization());

        assertThat(processCpuTime)
                .as("the current JDK should expose process CPU time through its OS MXBean")
                .isNotNull();
        assertThat(measurements)
                .as("at least one CPU supplier should reflectively invoke an OS MXBean method")
                .isNotEmpty()
                .allSatisfy(measurement -> assertThat(measurement).isNotNull());
    }

    private static <T extends Number> void collectMeasurement(
            List<Number> measurements, Supplier<T> supplier) {
        if (supplier != null) {
            measurements.add(supplier.get());
        }
    }
}
