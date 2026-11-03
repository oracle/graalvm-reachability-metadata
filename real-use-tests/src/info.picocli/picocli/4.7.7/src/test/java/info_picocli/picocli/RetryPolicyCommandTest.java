/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises picocli's custom type conversion for a user-facing retry policy.
public class RetryPolicyCommandTest {
    @Test
    public void convertsRetryDelayAndReportsPolicy() {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new BackupCommand());
        commandLine.setOut(new PrintWriter(output));
        commandLine.setErr(new PrintWriter(error));

        int exitCode = commandLine.execute(
                "--repository", "nightly", "--retry-delay", "45s", "--retries", "3");

        assertEquals(CommandLine.ExitCode.OK, exitCode, error.toString());
        assertEquals("repository=nightly\nretry-delay=PT45S\nretries=3\n", output.toString());
        assertEquals("", error.toString());
    }

    @Command(name = "backup", description = "Back up a repository with retry settings.")
    static class BackupCommand implements Runnable {
        @Spec
        CommandSpec spec;

        @Option(names = "--repository", required = true)
        String repository;

        @Option(names = "--retry-delay", required = true, converter = RetryDelayConverter.class)
        Duration retryDelay;

        @Option(names = "--retries", defaultValue = "0")
        int retries;

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("repository=" + repository);
            out.println("retry-delay=" + retryDelay);
            out.println("retries=" + retries);
        }
    }

    static class RetryDelayConverter implements ITypeConverter<Duration> {
        @Override
        public Duration convert(String value) {
            if (!value.endsWith("s")) {
                throw new CommandLine.TypeConversionException("Retry delay must end in seconds: " + value);
            }
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
    }
}
