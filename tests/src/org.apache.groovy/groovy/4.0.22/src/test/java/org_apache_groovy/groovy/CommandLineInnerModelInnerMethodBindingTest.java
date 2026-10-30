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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerModelInnerMethodBindingTest {
    @Test
    void passesConvertedValuesToAnAnnotatedSetter() {
        Options options = new Options();
        new CommandLine(options).parseArgs("--label", "ledger");
        assertEquals("LEDGER", options.label);
    }

    public static class Options {
        private String label;

        @Option(names = "--label")
        public void setLabel(String value) {
            label = value.toUpperCase(Locale.ROOT);
        }
    }
}
