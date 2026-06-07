/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Command;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

public class CommandLineInnerInterpreterTest {
    @Test
    void parsesAnnotatedCommandFieldsAndDeclaredSubcommand() {
        final ParserCommand command = new ParserCommand();
        final CommandLine commandLine = new CommandLine(command);

        assertThat(commandLine.getSubcommands()).containsKey("generated");

        final List<CommandLine> parsedCommands = commandLine.parse(
                "-v",
                "--name", "parsed",
                "--numbers", "2,3",
                "--tags", "red,blue",
                "--scores", "first=10,second=20",
                "generated");

        assertThat(parsedCommands).hasSize(2);
        assertThat(command.verbose).isTrue();
        assertThat(command.name).isEqualTo("parsed");
        assertThat(command.numbers).containsExactly(1, 2, 3);
        assertThat(command.tags).containsExactly("red", "blue");
        assertThat(command.scores).containsEntry("first", 10).containsEntry("second", 20);
    }

    @Command(name = "parser", subcommands = GeneratedSubcommand.class)
    public static final class ParserCommand {
        @Option(names = "-v")
        private boolean verbose;

        @Option(names = "--name")
        private String name = "initial";

        @Option(names = "--numbers", split = ",")
        private int[] numbers = {1};

        @Option(names = "--tags", split = ",")
        private ArrayList<String> tags;

        @Option(names = "--scores", split = ",")
        private HashMap<String, Integer> scores;
    }

    @Command(name = "generated")
    public static final class GeneratedSubcommand {
        public GeneratedSubcommand() {
        }
    }
}
