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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandLineInnerModelInnerCommandUserObjectTest {
    @Test
    void implementsAnAnnotatedOptionsInterface() {
        Settings settings = CommandLine.populateSpec(Settings.class, "--name", "report", "--count", "3");
        assertEquals("report", settings.name());
        assertEquals(3, settings.count());
    }

    @Command(name = "settings")
    public interface Settings {
        @Option(names = "--name")
        String name();

        @Option(names = "--count")
        int count();
    }
}
