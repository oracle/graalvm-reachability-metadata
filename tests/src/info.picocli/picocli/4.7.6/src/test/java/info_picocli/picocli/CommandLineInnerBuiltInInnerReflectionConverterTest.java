/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerBuiltInInnerReflectionConverterTest {

    @Test
    void convertsBuiltInTypesUsingReflectionBackedConverters() {
        ReflectionBackedBuiltInsCommand command = new ReflectionBackedBuiltInsCommand();

        new CommandLine(command).parseArgs("--date", "2024-02-29", "--path", "reports");

        assertThat(command.date).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(command.path).isEqualTo(Paths.get("reports"));
    }

    public static class ReflectionBackedBuiltInsCommand {
        @Option(names = "--date", required = true)
        private LocalDate date;

        @Option(names = "--path", required = true)
        private Path path;
    }
}
