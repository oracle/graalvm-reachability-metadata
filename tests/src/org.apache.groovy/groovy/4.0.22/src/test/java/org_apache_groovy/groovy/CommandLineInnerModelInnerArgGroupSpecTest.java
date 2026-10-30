/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarpicocli.CommandLine;
import groovyjarjarpicocli.CommandLine.ArgGroup;
import groovyjarjarpicocli.CommandLine.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerModelInnerArgGroupSpecTest {
    @Test
    void constructsAnArrayOfRepeatedArgumentGroups() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--name", "first", "--size", "2", "--name", "second", "--size", "5");
        assertEquals(2, options.entries.length);
        assertEquals("first", options.entries[0].name);
        assertEquals(2, options.entries[0].size);
        assertEquals("second", options.entries[1].name);
        assertEquals(5, options.entries[1].size);
    }

    public static class Options {
        @ArgGroup(exclusive = false, multiplicity = "1..*")
        public Entry[] entries;
    }

    public static class Entry {
        @Option(names = "--name", required = true)
        public String name;

        @Option(names = "--size", required = true)
        public int size;
    }
}
