/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.apache.commons.collections4.comparators.FixedOrderComparator;
import org.apache.commons.collections4.comparators.TransformingComparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the comparator subsystem for ordering a support queue by workflow priority.
 */
public class SupportQueueComparatorTest {
    @Test
    @Timeout(60)
    public void ordersTicketsByStatusSeverityAndSubject() {
        FixedOrderComparator<String> statusOrder = new FixedOrderComparator<>(
                "escalated", "assigned", "new");
        ComparatorChain<Ticket> queueOrder = new ComparatorChain<>();
        queueOrder.addComparator(new TransformingComparator<Ticket, String>(Ticket::status, statusOrder));
        queueOrder.addComparator(new TransformingComparator<Ticket, Integer>(
                Ticket::severity, Comparator.reverseOrder()));
        queueOrder.addComparator(new TransformingComparator<Ticket, String>(Ticket::subject));

        List<Ticket> queue = new ArrayList<>(List.of(
                new Ticket("T-104", "new", 5, "Billing export"),
                new Ticket("T-101", "assigned", 2, "Account locked"),
                new Ticket("T-103", "escalated", 3, "Checkout timeout"),
                new Ticket("T-102", "assigned", 4, "Address update"),
                new Ticket("T-105", "new", 5, "API token")));

        queue.sort(queueOrder);

        assertEquals(List.of("T-103", "T-102", "T-101", "T-105", "T-104"),
                queue.stream().map(Ticket::id).toList());
        assertTrue(queueOrder.isLocked());
        assertTrue(statusOrder.isLocked());
        assertEquals("Checkout timeout", queue.get(0).subject());
        assertEquals("API token", queue.get(3).subject());
    }

    private static final class Ticket {
        private final String id;
        private final String status;
        private final int severity;
        private final String subject;

        private Ticket(String id, String status, int severity, String subject) {
            this.id = id;
            this.status = status;
            this.severity = severity;
            this.subject = subject;
        }

        private String id() {
            return id;
        }

        private String status() {
            return status;
        }

        private int severity() {
            return severity;
        }

        private String subject() {
            return subject;
        }
    }
}
