/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestInfraLoggingUtilsTests {

    @Test
    void formatTestInfraCommandIncludesFutureDefaultsMode() {
        assertThat(TestInfraLoggingUtils.formatTestInfraCommand(
                "future-defaults-all",
                "com.example:demo:1.0.0",
                4
        )).isEqualTo("GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all ./gradlew testInfra -Pcoordinates=com.example:demo:1.0.0 -Pparallelism=4 --stacktrace");
    }

    @Test
    void formatTestInfraCommandOmitsDefaultModePrefix() {
        assertThat(TestInfraLoggingUtils.formatTestInfraCommand(
                NativeImageConfigUtils.DEFAULT_MODE,
                "com.example:demo:1.0.0",
                4
        )).isEqualTo("./gradlew testInfra -Pcoordinates=com.example:demo:1.0.0 -Pparallelism=4 --stacktrace");
    }

    @Test
    void batchReproducerLinesWrapTestInfraCommandInVisibleDelimiters() {
        assertThat(TestInfraLoggingUtils.batchReproducerLines(
                "testInfra",
                "future-defaults-all",
                "com.example:demo:1.0.0",
                4
        )).isEqualTo(List.of(
                "COORDINATES: com.example:demo:1.0.0",
                TestInfraLoggingUtils.DELIMITER,
                "Task: testInfra",
                "Native image mode: future-defaults-all",
                "Reproducer command: GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all ./gradlew testInfra -Pcoordinates=\"$COORDINATES\" -Pparallelism=4 --stacktrace",
                TestInfraLoggingUtils.DELIMITER
        ));
    }

    @Test
    void batchReproducerLinesUseInvokedTaskName() {
        assertThat(TestInfraLoggingUtils.batchReproducerLines(
                "test",
                "future-defaults-all",
                "com.example:demo:1.0.0",
                null
        )).isEqualTo(List.of(
                "COORDINATES: com.example:demo:1.0.0",
                TestInfraLoggingUtils.DELIMITER,
                "Task: test",
                "Native image mode: future-defaults-all",
                "Reproducer command: GVM_TCK_NATIVE_IMAGE_MODE=future-defaults-all ./gradlew test -Pcoordinates=\"$COORDINATES\" --stacktrace",
                TestInfraLoggingUtils.DELIMITER
        ));
    }

    @Test
    void parseParallelismFallsBackToDefault() {
        assertThat(TestInfraLoggingUtils.parseParallelism("")).isEqualTo(4);
    }

    @Test
    void parseParallelismRejectsInvalidValues() {
        assertThatThrownBy(() -> TestInfraLoggingUtils.parseParallelism("zero"))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("must be a positive integer");
    }
}
