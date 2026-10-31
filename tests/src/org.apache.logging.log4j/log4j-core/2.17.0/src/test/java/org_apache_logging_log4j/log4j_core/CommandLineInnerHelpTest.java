/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.tools.picocli.CommandLine.Command;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Help;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Help.Ansi;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Option;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineInnerHelpTest {
    @Test
    void buildsUsageSectionsFromCommandAndFieldAnnotations() {
        Help help = new Help(new ExportCommand(), Ansi.OFF);
        String synopsis = help.synopsis(0);
        assertTrue(synopsis.contains("export"));
        assertTrue(synopsis.contains("DESTINATION"));
        assertTrue(help.optionList().contains("--format"));
        assertTrue(help.description().contains("Export application data"));
        assertTrue(help.parameterList().contains("Output destination"));
    }

    @Command(name = "export", description = "Export application data")
    public static class ExportCommand {
        @Option(names = "--format", description = "Output format")
        String format;

        @Parameters(index = "0", paramLabel = "DESTINATION", description = "Output destination")
        String destination;
    }
}
