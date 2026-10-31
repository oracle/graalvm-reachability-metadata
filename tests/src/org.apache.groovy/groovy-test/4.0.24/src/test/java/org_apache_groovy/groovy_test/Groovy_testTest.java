/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_test;

import groovy.test.GroovyAssert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Groovy_testTest {
    @Test
    public void testFailPreservesMessage() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> GroovyAssert.fail("Expected assertion failure"));
        assertEquals("Expected assertion failure", failure.getMessage());
    }

    @Test
    public void testFailWithoutMessage() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> GroovyAssert.fail(null));
        assertNull(failure.getMessage());
    }
}
