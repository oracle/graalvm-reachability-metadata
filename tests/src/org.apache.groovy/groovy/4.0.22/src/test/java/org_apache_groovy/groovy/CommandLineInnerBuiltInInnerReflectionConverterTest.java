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

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerBuiltInInnerReflectionConverterTest {
    @Test
    void convertsJdkTypesUsingTheirStringFactories() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--duration", "PT15M", "--date", "2024-02-29", "--path", "inventory.txt");
        assertEquals(Duration.ofMinutes(15), options.duration);
        assertEquals(LocalDate.of(2024, 2, 29), options.date);
        assertEquals(Path.of("inventory.txt"), options.path);
    }

    public static class Options {
        @Option(names = "--duration")
        public Duration duration;

        @Option(names = "--date")
        public LocalDate date;

        @Option(names = "--path")
        public Path path;
    }
}
