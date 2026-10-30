/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_quiesce.org_apache_aries_quiesce_api;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.aries.quiesce.manager.QuiesceCallback;
import org.apache.aries.quiesce.manager.QuiesceManager;
import org.apache.aries.quiesce.manager.impl.QuiesceManagerImpl;
import org.apache.aries.quiesce.participant.QuiesceParticipant;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuiesceCallbackTest {
    @Test
    void stopsEachBundleOnlyAfterEveryParticipantAcknowledgesIt() throws Exception {
        QuiesceTestBundle first = new QuiesceTestBundle();
        QuiesceTestBundle second = new QuiesceTestBundle();
        CountDownLatch invoked = new CountDownLatch(2);
        AtomicReference<QuiesceCallback> firstCallback = new AtomicReference<>();
        AtomicReference<QuiesceCallback> secondCallback = new AtomicReference<>();
        QuiesceParticipant firstParticipant = (callback, bundles) -> {
            firstCallback.set(callback);
            invoked.countDown();
        };
        QuiesceParticipant secondParticipant = (callback, bundles) -> {
            secondCallback.set(callback);
            invoked.countDown();
        };
        QuiesceManager manager = new QuiesceManagerImpl(new QuiesceTestContext(firstParticipant, secondParticipant));

        Future<?> completion = manager.quiesceWithFuture(30_000, List.of(first, second));

        assertTrue(invoked.await(10, TimeUnit.SECONDS));
        assertEquals(Bundle.ACTIVE, first.getState());
        assertEquals(Bundle.ACTIVE, second.getState());
        assertFalse(completion.isDone());

        firstCallback.get().bundleQuiesced(first, second);
        assertEquals(Bundle.ACTIVE, first.getState());
        assertEquals(Bundle.ACTIVE, second.getState());
        assertFalse(completion.isDone());

        secondCallback.get().bundleQuiesced(first);
        assertEquals(Bundle.RESOLVED, first.getState());
        assertEquals(Bundle.ACTIVE, second.getState());
        assertFalse(completion.isDone());

        secondCallback.get().bundleQuiesced(second);
        assertNull(completion.get(10, TimeUnit.SECONDS));
        assertTrue(completion.isDone());
        assertEquals(Bundle.RESOLVED, second.getState());
        assertEquals(1, first.stopCount());
        assertEquals(1, second.stopCount());
    }
}
