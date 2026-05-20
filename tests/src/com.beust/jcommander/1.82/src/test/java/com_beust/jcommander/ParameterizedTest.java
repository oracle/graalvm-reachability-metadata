/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameterized;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterizedTest {
    @Test
    void fieldBackedParameterCanBeReadAndWritten() {
        CommandOptions options = new CommandOptions();
        Parameterized parameterized = parameterNamed(Parameterized.parseArg(options), "fieldValue");

        parameterized.set(options, "configured");

        assertThat(parameterized.get(options)).isEqualTo("configured");
    }

    @Test
    void methodBackedParameterCanUseSetterAndMatchingGetter() {
        CommandOptions options = new CommandOptions();
        Parameterized parameterized = parameterNamed(Parameterized.parseArg(options), "setLevel");

        parameterized.set(options, 42);

        assertThat(parameterized.get(options)).isEqualTo(42);
    }

    @Test
    void methodBackedBooleanParameterCanUseIsGetter() {
        CommandOptions options = new CommandOptions();
        Parameterized parameterized = parameterNamed(Parameterized.parseArg(options), "setVerbose");

        parameterized.set(options, true);

        assertThat(parameterized.get(options)).isEqualTo(true);
    }

    @Test
    void methodBackedParameterFallsBackToMatchingFieldWhenGetterIsMissing() {
        CommandOptions options = new CommandOptions();
        Parameterized parameterized = parameterNamed(Parameterized.parseArg(options), "setToken");

        parameterized.set(options, "secret-token");

        assertThat(parameterized.get(options)).isEqualTo("secret-token");
    }

    private static Parameterized parameterNamed(List<Parameterized> parameters, String name) {
        return parameters.stream()
                .filter(parameterized -> name.equals(parameterized.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing parameterized member: " + name));
    }

    public static class CommandOptions {
        @Parameter(names = "--field")
        private String fieldValue;

        private int level;

        private boolean verbose;

        private String token;

        @Parameter(names = "--level")
        public void setLevel(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }

        @Parameter(names = "--verbose")
        public void setVerbose(boolean verbose) {
            this.verbose = verbose;
        }

        public boolean isVerbose() {
            return verbose;
        }

        @Parameter(names = "--token")
        public void setToken(String token) {
            this.token = token;
        }
    }
}
