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

import java.sql.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerBuiltInInnerISO8601TimeConverterTest {
    @Test
    void convertsATimeOptionToSqlTime() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--time", "13:45:20");
        assertEquals("13:45:20", options.time.toString());
    }

    public static class Options {
        @Option(names = "--time")
        public Time time;
    }
}
