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

public class CommandLineInnerModelInnerUsageMessageSpecAnonymous1Test {
    @Test
    void wrapsUsageUsingAutomaticTerminalWidth() {
        CommandSpec spec = CommandSpec.create().name("dispatch");
        spec.usageMessage().autoWidth(true).width(64);
        spec.addOption(OptionSpec.builder("--destination").type(String.class)
                .description("Address of the depot that receives the shipment and records its delivery confirmation")
                .build());
        int width = spec.usageMessage().width();
        String usage = new CommandLine(spec).getUsageMessage(Ansi.OFF);
        assertTrue(usage.contains("--destination"));
        assertTrue(usage.contains("confirmation"));
        assertTrue(usage.lines().allMatch(line -> line.length() <= width));
    }
}
