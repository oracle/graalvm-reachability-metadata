/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerInterpreterTest {

    @Test
    void expandsArrayOptionValues() {
        ArrayOptionCommand command = new ArrayOptionCommand();

        new CommandLine(command).parseArgs("--tag", "alpha", "--tag", "beta,gamma");

        assertThat(command.tags).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void togglesOptionalBooleanFromInitialValue() {
        OptionalBooleanCommand command = new OptionalBooleanCommand();

        new CommandLine(command).parseArgs("--enabled");

        assertThat(command.enabled).contains(true);
    }

    @Test
    void readsInteractivePasswordWithoutEcho() {
        PasswordCommand command = new PasswordCommand();
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("s3cr3t\n".getBytes(StandardCharsets.UTF_8)));
        try {
            new CommandLine(command).parseArgs("--password");
        } finally {
            System.setIn(originalIn);
        }

        assertThat(command.password).containsExactly('s', '3', 'c', 'r', '3', 't');
    }

    public static class ArrayOptionCommand {
        @Option(names = "--tag", split = ",")
        private String[] tags;
    }

    public static class OptionalBooleanCommand {
        @Option(names = "--enabled", arity = "0..1")
        private Optional<Boolean> enabled = Optional.of(false);
    }

    public static class PasswordCommand {
        @Option(names = "--password", arity = "0", interactive = true)
        private char[] password;
    }
}
