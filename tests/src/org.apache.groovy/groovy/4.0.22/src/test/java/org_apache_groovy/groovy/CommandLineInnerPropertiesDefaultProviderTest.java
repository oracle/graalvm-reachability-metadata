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
import groovyjarjarpicocli.CommandLine.PropertiesDefaultProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerPropertiesDefaultProviderTest {
    @Test
    void readsClasspathDefaultsAndLetsExplicitOptionsOverrideThem() {
        Inventory options = new Inventory();
        CommandLine command = new CommandLine(options);
        command.parseArgs();
        assertEquals(11, options.count);
        command.parseArgs("--count", "4");
        assertEquals(4, options.count);
    }

    @Command(name = "forge-inventory-defaults", defaultValueProvider = PropertiesDefaultProvider.class)
    public static class Inventory {
        @Option(names = "--count")
        public int count;
    }
}
