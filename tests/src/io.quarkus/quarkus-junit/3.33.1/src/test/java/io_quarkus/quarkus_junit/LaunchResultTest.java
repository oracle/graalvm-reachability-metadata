/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.quarkus.test.junit.main.LaunchResult;
import org.junit.jupiter.api.Test;

public class LaunchResultTest {
    @Test
    void joinsCapturedOutputStreamsWithNewlines() {
        LaunchResult result = new SimpleLaunchResult(
                List.of("first line", "second line"),
                List.of("warning", "detail"),
                7);

        assertThat(result.getOutput()).isEqualTo("first line\nsecond line");
        assertThat(result.getErrorOutput()).isEqualTo("warning\ndetail");
        assertThat(result.exitCode()).isEqualTo(7);
    }

    private static final class SimpleLaunchResult implements LaunchResult {
        private final List<String> output;
        private final List<String> errorOutput;
        private final int exitCode;

        private SimpleLaunchResult(List<String> output, List<String> errorOutput, int exitCode) {
            this.output = output;
            this.errorOutput = errorOutput;
            this.exitCode = exitCode;
        }

        @Override
        public List<String> getOutputStream() {
            return output;
        }

        @Override
        public List<String> getErrorStream() {
            return errorOutput;
        }

        @Override
        public int exitCode() {
            return exitCode;
        }
    }
}
