/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Help.Ansi;
import groovyjarjarpicocli.CommandLine.Model.CommandSpec;
import groovyjarjarpicocli.CommandLine.Model.OptionSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineInnerHelpInnerAnsiTest {
    @Test
    void rendersAutomaticTerminalStylingWithoutChangingTheUsageContent() {
        CommandSpec spec = CommandSpec.create().name("dispatch");
        spec.addOption(OptionSpec.builder("--count").type(int.class).description("Number of parcels").build());
        String usage = new CommandLine(spec).getUsageMessage(Ansi.AUTO).replaceAll("\u001B\\[[0-9;]*m", "");
        assertTrue(usage.contains("dispatch"));
        assertTrue(usage.contains("--count"));
        assertTrue(usage.contains("Number of parcels"));
    }
}
