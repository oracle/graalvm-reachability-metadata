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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerInterpreterTest {
    @Test
    void collectsRepeatedOptionsIntoArraysAndParsesOptionalBooleans() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--numbers", "3,5", "--numbers", "8", "--enabled");
        assertArrayEquals(new Integer[]{3, 5, 8}, options.numbers);
        assertEquals(Optional.of(true), options.enabled);
    }

    public static class Options {
        @Option(names = "--numbers", split = ",")
        public Integer[] numbers;

        @Option(names = "--enabled")
        public Optional<Boolean> enabled;
    }
}
