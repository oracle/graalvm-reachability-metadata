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

public class CommandLineInnerBuiltInInnerClassConverterTest {
    @Test
    void convertsAClassNameOptionIntoAClass() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--type", "java.lang.String");
        assertEquals(String.class, options.type);
        assertEquals("value", options.type.cast("value"));
    }

    public static class Options {
        @Option(names = "--type")
        public Class<?> type;
    }
}
