/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerModelInnerFieldBindingTest {
    @Test
    void bindsAnnotatedFieldsAndResetsDefaultsBetweenParses() {
        Options options = new Options();
        CommandLine command = new CommandLine(options);
        command.parseArgs("--count", "12");
        assertEquals(12, options.count);
        command.parseArgs();
        assertEquals(4, options.count);
    }

    public static class Options {
        @Option(names = "--count", defaultValue = "4")
        private int count;
    }
}
