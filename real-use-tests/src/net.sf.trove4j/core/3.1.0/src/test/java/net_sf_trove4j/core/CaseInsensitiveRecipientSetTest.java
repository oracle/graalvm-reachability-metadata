/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.set.hash.TCustomHashSet;
import gnu.trove.strategy.HashingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Trove's custom-hashing set subsystem to manage case-insensitive recipients.
public class CaseInsensitiveRecipientSetTest {
    @Test
    @Timeout(60)
    public void keepsOneSubscriptionPerEmailAddress() {
        TCustomHashSet<String> recipients = new TCustomHashSet<>(
                new CaseInsensitiveEmailStrategy());
        recipients.add("Alice@example.com");
        recipients.add("bob@example.com");
        boolean duplicateAdded = recipients.add("ALICE@EXAMPLE.COM");

        assertFalse(duplicateAdded);
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains("alice@EXAMPLE.com"));

        recipients.remove("BOB@EXAMPLE.COM");

        assertEquals(1, recipients.size());
        assertTrue(recipients.contains("Alice@example.com"));
        assertFalse(recipients.contains("bob@example.com"));
    }

    private static final class CaseInsensitiveEmailStrategy
            implements HashingStrategy<String> {
        @Override
        public int computeHashCode(String emailAddress) {
            return emailAddress.toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public boolean equals(String left, String right) {
            return left.equalsIgnoreCase(right);
        }
    }
}
