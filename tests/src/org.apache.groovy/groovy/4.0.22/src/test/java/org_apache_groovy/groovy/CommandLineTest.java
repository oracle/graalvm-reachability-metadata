/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineTest {
    @Test
    void executesACommandDiscoveredByItsMethodName() {
        CommandLine command = new CommandLine(CommandLine.getCommandMethods(Commands.class, "square").get(0));
        assertEquals(36, command.execute("--value", "6"));
        assertEquals(Integer.valueOf(36), command.getExecutionResult());
    }

    @Test
    void invokesAnnotatedInstanceAndStaticCommands() {
        Commands instance = new Commands();
        CommandLine root = new CommandLine(instance);
        assertEquals(0, root.execute("greet", "--name", "Ada"));
        assertEquals("Hello Ada", root.getSubcommands().get("greet").getExecutionResult());
        assertEquals(0, root.execute("greet"));
        assertEquals("Hello visitor", root.getSubcommands().get("greet").getExecutionResult());
        assertEquals(49, root.execute("square", "--value", "7"));
        assertEquals(Integer.valueOf(49), root.getSubcommands().get("square").getExecutionResult());
    }

    @Command(name = "commands")
    public static class Commands {
        @Command(name = "greet")
        public String greet(@Option(names = "--name") Optional<String> name) {
            return "Hello " + name.orElse("visitor");
        }

        @Command(name = "square")
        public static int square(@Option(names = "--value") int value) {
            return value * value;
        }
    }
}
