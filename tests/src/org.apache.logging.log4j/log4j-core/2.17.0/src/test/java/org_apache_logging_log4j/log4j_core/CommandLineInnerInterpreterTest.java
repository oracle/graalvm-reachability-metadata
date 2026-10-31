/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Command;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineInnerInterpreterTest {
    @Test
    void populatesScalarArrayCollectionMapAndDeclarativeSubcommand() {
        Application command = new Application();
        CommandLine parser = new CommandLine(command);
        List<CommandLine> parsed = parser.parse("-v", "--name", "batch", "--numbers", "2,3",
                "--tags", "blue,green", "--settings", "retries=4", "send", "--limit", "9");
        assertTrue(command.verbose);
        assertEquals("batch", command.name);
        assertArrayEquals(new int[]{1, 2, 3}, command.numbers);
        assertEquals(List.of("blue", "green"), command.tags);
        assertEquals(Map.of("retries", 4), command.settings);
        Send subcommand = assertInstanceOf(Send.class, parsed.getLast().getCommand());
        assertEquals(9, subcommand.limit);
        assertEquals(parser, parsed.getLast().getParent());
    }

    @Command(name = "application", subcommands = Send.class)
    public static class Application {
        @Option(names = "-v")
        boolean verbose;

        @Option(names = "--name")
        String name;

        @Option(names = "--numbers", split = ",", arity = "1")
        int[] numbers = {1};

        @Option(names = "--tags", split = ",", arity = "1")
        ArrayList<String> tags;

        @Option(names = "--settings", arity = "1")
        LinkedHashMap<String, Integer> settings;
    }

    @Command(name = "send")
    public static class Send {
        @Option(names = "--limit")
        int limit;
    }
}
