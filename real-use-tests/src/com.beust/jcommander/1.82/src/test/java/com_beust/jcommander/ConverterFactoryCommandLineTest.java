/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.IStringConverterFactory;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's converter-factory subsystem for an operations CLI with application-specific value types.
public class ConverterFactoryCommandLineTest {
    @Test
    public void convertsAnApplicationSpecificDurationWithARegisteredFactory() {
        RunOptions options = new RunOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("operations-tool")
                .addConverterFactory(new ApplicationConverterFactory())
                .addObject(options)
                .build();

        commander.parse("--timeout", "30s");

        assertEquals(Duration.ofSeconds(30), options.timeout);
    }

    @Parameters(commandDescription = "Run an operation with a bounded timeout")
    public static class RunOptions {
        @Parameter(names = "--timeout", description = "maximum operation duration", required = true)
        private Duration timeout;
    }

    public static class ApplicationConverterFactory implements IStringConverterFactory {
        @Override
        public Class<? extends IStringConverter<?>> getConverter(Class<?> type) {
            return type == Duration.class ? DurationConverter.class : null;
        }
    }

    public static class DurationConverter implements IStringConverter<Duration> {
        @Override
        public Duration convert(String value) {
            if (!value.endsWith("s")) {
                throw new IllegalArgumentException("Duration must end with s");
            }
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
    }
}
