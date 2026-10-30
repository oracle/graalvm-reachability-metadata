/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.vmplugin.v8.PluginDefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginDefaultGroovyMethodsTest {
    @Test
    void advancesAndRewindsEnumsWithWraparound() {
        assertEquals(Stage.REVIEW, PluginDefaultGroovyMethods.next(Stage.DRAFT));
        assertEquals(Stage.DRAFT, PluginDefaultGroovyMethods.next(Stage.REVIEW));
        assertEquals(Stage.REVIEW, PluginDefaultGroovyMethods.previous(Stage.DRAFT));
        assertEquals(Stage.DRAFT, PluginDefaultGroovyMethods.previous(Stage.REVIEW));
    }

    public enum Stage {
        DRAFT, REVIEW
    }
}
