/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_quiesce.org_apache_aries_quiesce_api;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.aries.quiesce.manager.QuiesceManager;
import org.apache.aries.quiesce.manager.impl.QuiesceManagerImpl;
import org.apache.aries.quiesce.participant.QuiesceParticipant;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuiesceParticipantTest {
    @Test
    void participantReceivesRequestedBundlesAndAcknowledgesCompletion() throws Exception {
        QuiesceTestBundle first = new QuiesceTestBundle();
        QuiesceTestBundle second = new QuiesceTestBundle();
        List<Bundle> requested = List.of(first, second);
        AtomicReference<List<Bundle>> received = new AtomicReference<>();
        QuiesceParticipant participant = (callback, bundles) -> {
            received.set(List.copyOf(bundles));
            callback.bundleQuiesced(bundles.toArray(Bundle[]::new));
        };
        QuiesceManager manager = new QuiesceManagerImpl(new QuiesceTestContext(participant));

        Future<?> completion = manager.quiesceWithFuture(30_000, requested);

        assertNull(completion.get(10, TimeUnit.SECONDS));
        assertEquals(2, received.get().size());
        assertTrue(received.get().containsAll(requested));
        assertEquals(Bundle.RESOLVED, first.getState());
        assertEquals(Bundle.RESOLVED, second.getState());
        assertEquals(1, first.stopCount());
        assertEquals(1, second.stopCount());
        assertEquals(List.of(first, second), requested);
    }
}
