/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;

public class JUnit4Scenario {
    static final AtomicInteger executions = new AtomicInteger();

    @Test
    public void addsNumbers() {
        assertEquals(9, 4 + 5);
        executions.incrementAndGet();
    }

    @Test
    @Ignore("awaiting an optional integration service")
    public void optionalIntegration() {
        throw new AssertionError("An ignored test must not execute");
    }
}
