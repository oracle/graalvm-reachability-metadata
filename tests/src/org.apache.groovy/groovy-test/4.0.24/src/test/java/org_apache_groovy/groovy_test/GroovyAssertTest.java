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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyAssertTest {
    private int expectedFailureInvocations;
    private boolean expectedFailureBodyReached;
    private int unexpectedSuccessInvocations;
    private boolean unexpectedSuccessBodyReached;

    @Test
    public void testNotYetImplementedAcceptsExpectedFailure() {
        expectedFailureInvocations++;
        if (GroovyAssert.notYetImplemented(this)) {
            assertEquals(2, expectedFailureInvocations);
            assertTrue(expectedFailureBodyReached);
            return;
        }

        expectedFailureBodyReached = true;
        throw new IllegalStateException("Deliberate exception for the expected-failure helper");
    }

    @Test
    public void testNotYetImplementedRejectsUnexpectedSuccess() {
        unexpectedSuccessInvocations++;
        if (unexpectedSuccessInvocations == 2) {
            assertFalse(GroovyAssert.notYetImplemented(this));
            unexpectedSuccessBodyReached = true;
            return;
        }

        AssertionError failure = assertThrows(AssertionError.class,
                () -> GroovyAssert.notYetImplemented(this));
        assertEquals("testNotYetImplementedRejectsUnexpectedSuccess"
                + " is marked as not yet implemented but passes unexpectedly", failure.getMessage());
        assertEquals(2, unexpectedSuccessInvocations);
        assertTrue(unexpectedSuccessBodyReached);
    }
}
