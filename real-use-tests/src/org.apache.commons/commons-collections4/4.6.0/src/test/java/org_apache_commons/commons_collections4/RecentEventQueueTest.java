/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the bounded queue subsystem for retaining the most recent user events.
 */
public class RecentEventQueueTest {
    @Test
    @Timeout(60)
    public void retainsOnlyTheMostRecentEventsAtCapacity() {
        CircularFifoQueue<String> recentEvents = new CircularFifoQueue<>(3);
        recentEvents.offer("login");
        recentEvents.offer("search");
        recentEvents.offer("checkout");

        assertTrue(recentEvents.isAtFullCapacity());
        assertEquals(3, recentEvents.size());
        assertEquals("login", recentEvents.peek());
        assertEquals(List.of("login", "search", "checkout"), List.copyOf(recentEvents));

        assertTrue(recentEvents.offer("logout"));

        assertEquals(3, recentEvents.size());
        assertEquals("search", recentEvents.peek());
        assertEquals(List.of("search", "checkout", "logout"), List.copyOf(recentEvents));
        assertEquals("search", recentEvents.poll());
        assertEquals(List.of("checkout", "logout"), List.copyOf(recentEvents));
    }
}
