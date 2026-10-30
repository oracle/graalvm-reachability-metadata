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

public class CommandLineInnerModelInnerCommandReflectionTest {
    @Test
    void discoversInheritedFieldsAndAnnotatedSetterMethods() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--name", "warehouse", "--limit", "9");
        assertEquals("warehouse", options.name);
        assertEquals(9, options.limit);
    }

    public static class BaseOptions {
        @Option(names = "--name")
        public String name;
    }

    public static class Options extends BaseOptions {
        private int limit;

        @Option(names = "--limit")
        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
