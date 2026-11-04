/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Trove's primitive-set subsystem to select notification recipients.
public class PrimitiveSetAudienceTest {
    @Test
    @Timeout(60)
    public void selectsUniqueOptedInRecipients() {
        TLongHashSet recentlyActive = new TLongHashSet();
        recentlyActive.add(501L);
        recentlyActive.add(502L);
        recentlyActive.add(501L);
        recentlyActive.add(503L);
        recentlyActive.add(504L);

        TLongHashSet optedIn = new TLongHashSet(new long[]{501L, 503L, 505L});
        TLongHashSet suppressed = new TLongHashSet(new long[]{503L});

        recentlyActive.retainAll(optedIn);
        recentlyActive.removeAll(suppressed);

        long[] recipients = recentlyActive.toArray();
        Arrays.sort(recipients);

        assertEquals(1, recentlyActive.size());
        assertTrue(recentlyActive.contains(501L));
        assertArrayEquals(new long[]{501L}, recipients);

        TLongIterator selected = recentlyActive.iterator();
        long recipientIdTotal = 0L;
        while (selected.hasNext()) {
            recipientIdTotal += selected.next();
        }
        assertEquals(501L, recipientIdTotal);
    }
}
