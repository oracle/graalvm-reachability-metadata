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
import groovyjarjarpicocli.CommandLine.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerModelInnerMethodParamTest {
    @Test
    void mapsOptionsAndPositionalsOntoCommandMethodParameters() {
        CommandLine command = new CommandLine(new Application());
        assertEquals(0, command.execute("repeat", "--times", "3", "ha"));
        assertEquals("hahaha", command.getSubcommands().get("repeat").getExecutionResult());
    }

    @Command(name = "app")
    public static class Application {
        @Command(name = "repeat")
        public String repeat(@Option(names = "--times") int times, @Parameters(index = "0") String text) {
            return text.repeat(times);
        }
    }
}
