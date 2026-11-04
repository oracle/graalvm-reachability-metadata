/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import org.junit.jupiter.api.Test;

/**
 * SLA budget validation is the request admission subsystem exercised here.
 *
 * A request gateway parses a client's protobuf duration budget, rejects
 * non-positive values, and reports the budget remaining after admission.
 */
public class SlaBudgetValidationTest {

    @Test
    void admitsRequestAndReportsRemainingBudget() throws Exception {
        Duration configuredBudget = Durations.parse("3.000s");
        Duration requestBudget = Durations.parse("2.500s");

        assertTrue(Durations.isValid(configuredBudget));
        assertTrue(Durations.isPositive(requestBudget));
        Durations.checkPositive(requestBudget);
        assertTrue(Durations.compare(requestBudget, configuredBudget) < 0);

        Duration remainingBudget = Durations.subtract(configuredBudget, requestBudget);

        assertEquals("0.500s", Durations.toString(remainingBudget));
        assertEquals(500L, Durations.toMillis(remainingBudget));
        assertEquals(Duration.newBuilder().setNanos(500_000_000).build(), remainingBudget);
    }

    @Test
    void rejectsNonPositiveRequestBudget() {
        Duration zeroBudget = Durations.ZERO;

        assertTrue(Durations.isValid(zeroBudget));
        assertThrows(IllegalArgumentException.class, () -> Durations.checkPositive(zeroBudget));
    }
}
