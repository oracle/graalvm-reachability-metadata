/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.stack.array.TLongArrayStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Trove's primitive-stack subsystem for an undo history of revisions.
public class PrimitiveUndoStackTest {
    @Test
    @Timeout(60)
    public void restoresRevisionsInLastInFirstOutOrder() {
        TLongArrayStack revisionHistory = new TLongArrayStack();
        revisionHistory.push(7001L);
        revisionHistory.push(7002L);
        revisionHistory.push(7003L);

        assertEquals(7003L, revisionHistory.peek());
        assertEquals(3, revisionHistory.size());

        long latestRevision = revisionHistory.pop();
        long previousRevision = revisionHistory.pop();

        assertEquals(7003L, latestRevision);
        assertEquals(7002L, previousRevision);
        assertArrayEquals(new long[]{7001L}, revisionHistory.toArray());
        assertEquals(1, revisionHistory.size());
    }
}
