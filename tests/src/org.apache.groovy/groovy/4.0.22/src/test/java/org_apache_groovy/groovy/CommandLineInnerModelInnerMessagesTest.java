/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Command;
import groovyjarjarpicocli.CommandLine.Help.Ansi;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineInnerModelInnerMessagesTest {
    @Test
    void localizesAnnotatedOptionDescriptions() {
        CommandLine command = new CommandLine(new Inventory());
        String usage = command.getUsageMessage(Ansi.OFF);
        assertTrue(usage.contains("Number of entries"));
        assertTrue(usage.contains("Localized inventory"));
    }

    @Command(name = "inventory", resourceBundle = "org_apache_groovy.groovy.messages")
    public static class Inventory {
        @Option(names = "--count", descriptionKey = "count")
        public int count;
    }
}
