/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringGroovyMethodsTest {
    @Test
    void stripsIndentUsingTheJdkStringOperation() {
        assertEquals("alpha\nbeta\n", StringGroovyMethods.stripIndent("    alpha\n    beta\n    ", false));
    }
}
