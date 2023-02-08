/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestLifetimeExceededException {

    @Test
    public void testLifetimeExceededException() {
        final LifetimeExceededException exception = new LifetimeExceededException("car");
        assertEquals("car", exception.getMessage());
    }

    @Test
    public void testLifetimeExceededExceptionNoMessage() {
        final LifetimeExceededException exception = new LifetimeExceededException();
        assertNull(exception.getMessage());
    }
}
