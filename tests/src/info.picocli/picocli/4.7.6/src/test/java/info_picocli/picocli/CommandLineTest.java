/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineTest {

    @Test
    void findsPublicInheritedAndDeclaredCommandMethods() {
        List<String> methodNames = CommandLine.getCommandMethods(DeclaredMethodCommands.class, null).stream()
                .map(Method::getName)
                .collect(Collectors.toList());

        assertThat(methodNames)
                .contains("inherited", "declared");
    }

    @Test
    void executesStaticCommandMethod() {
        Object result = CommandLine.invoke("shout", StaticMethodCommands.class, "planet");

        assertThat(result).isEqualTo("PLANET");
    }

    @Test
    void executesInstanceCommandMethodAsSubcommand() {
        InstanceMethodCommands command = new InstanceMethodCommands();
        CommandLine commandLine = new CommandLine(command);

        int exitCode = commandLine.execute("greet", "picocli");

        assertThat(exitCode).isZero();
        assertThat(command.greeting).isEqualTo("Hello, picocli");
    }

    @Test
    void mapsOptionalMethodOptionsToEmptyAndPresentValues() {
        Object emptyResult = CommandLine.invoke("optional", OptionalMethodCommands.class);
        Object presentResult = CommandLine.invoke("optional", OptionalMethodCommands.class, "--name", "picocli");

        assertThat(emptyResult).isEqualTo(Optional.empty());
        assertThat(presentResult).isEqualTo(Optional.of("picocli"));
    }

    public static class BaseMethodCommands {
        @Command
        public void inherited() {
        }
    }

    public static class DeclaredMethodCommands extends BaseMethodCommands {
        @Command
        public void declared() {
        }

    }

    public static class StaticMethodCommands {
        @Command
        public static String shout(@Parameters String value) {
            return value.toUpperCase();
        }
    }

    @Command
    public static class InstanceMethodCommands {
        private String greeting;

        @Command
        public void greet(@Parameters String name) {
            greeting = "Hello, " + name;
        }
    }

    public static class OptionalMethodCommands {
        @Command
        public static Optional<String> optional(@Option(names = "--name") Optional<String> name) {
            return name;
        }
    }
}
