/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Option;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineTest {
    @Test
    void discoversInheritedOptionsAndPositionalArguments() {
        ImportCommand command = CommandLine.populateCommand(new ImportCommand(), "--count", "4", "input.txt");
        assertEquals(4, command.count);
        assertEquals("input.txt", command.input);
    }

    public static class BaseCommand {
        @Option(names = "--count")
        int count;
    }

    public static class ImportCommand extends BaseCommand {
        @Parameters(index = "0")
        String input;
    }
}
