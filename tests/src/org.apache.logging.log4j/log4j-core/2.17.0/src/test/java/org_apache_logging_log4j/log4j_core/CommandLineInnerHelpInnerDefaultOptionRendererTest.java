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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineInnerHelpInnerDefaultOptionRendererTest {
    @Test
    void displaysScalarAndArrayDefaultValuesInOptionHelp() {
        String options = new Help(new Application(), Ansi.OFF).optionList();
        assertTrue(options.contains("--workers"));
        assertTrue(options.contains("Default: 7"));
        assertTrue(options.contains("Default: [blue, green]"));
    }

    @Command(name = "application", showDefaultValues = true)
    public static class Application {
        @Option(names = "--workers", description = "Worker count")
        int workers = 7;

        @Option(names = "--tags", description = "Application tags")
        String[] tags = {"blue", "green"};
    }
}
