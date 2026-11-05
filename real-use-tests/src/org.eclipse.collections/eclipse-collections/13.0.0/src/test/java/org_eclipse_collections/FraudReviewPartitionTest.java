/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import java.util.List;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.partition.list.PartitionList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/// Exercises Eclipse Collections' partition subsystem to route orders through fraud review.
public class FraudReviewPartitionTest {
    @Test
    @Timeout(60)
    public void separatesOrdersForManualReviewWithoutChangingQueueOrder() {
        MutableList<Order> checkoutQueue = Lists.mutable.with(
                new Order("O-101", 12),
                new Order("O-102", 84),
                new Order("O-103", 41),
                new Order("O-104", 93));

        PartitionList<Order> fraudReview = checkoutQueue.partition(order -> order.riskScore() >= 80);

        assertIterableEquals(
                List.of("O-102", "O-104"),
                fraudReview.getSelected().collect(Order::id));
        assertIterableEquals(
                List.of("O-101", "O-103"),
                fraudReview.getRejected().collect(Order::id));
        assertEquals(2, fraudReview.getSelected().size());
        assertEquals(2, fraudReview.getRejected().size());
        assertEquals("O-102", fraudReview.getSelected().getFirst().id());
        assertEquals("O-103", fraudReview.getRejected().getLast().id());
    }

    private record Order(String id, int riskScore) {
    }
}
