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

import org.apache.aries.quiesce.manager.QuiesceManager;
import org.apache.aries.quiesce.manager.impl.QuiesceManagerImpl;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuiesceManagerTest {
    @Test
    void futureCompletesAfterStoppingBundleWithDefaultTimeout() throws Exception {
        QuiesceManager manager = new QuiesceManagerImpl(new QuiesceTestContext());
        QuiesceTestBundle bundle = new QuiesceTestBundle();

        Future<?> completion = manager.quiesceWithFuture(List.of(bundle));

        assertNull(completion.get(10, TimeUnit.SECONDS));
        assertTrue(completion.isDone());
        assertFalse(completion.isCancelled());
        assertEquals(Bundle.RESOLVED, bundle.getState());
        assertEquals(1, bundle.stopCount());
        assertThrows(UnsupportedOperationException.class, () -> completion.cancel(true));
    }

    @Test
    void futureCompletesAfterStoppingBundleWithExplicitTimeout() throws Exception {
        QuiesceManager manager = new QuiesceManagerImpl(new QuiesceTestContext());
        QuiesceTestBundle bundle = new QuiesceTestBundle();

        Future<?> completion = manager.quiesceWithFuture(30_000, List.of(bundle));

        assertNull(completion.get(10, TimeUnit.SECONDS));
        assertTrue(completion.isDone());
        assertEquals(Bundle.RESOLVED, bundle.getState());
        assertEquals(1, bundle.stopCount());
    }

    @Test
    void voidRequestStopsBundleWithDefaultTimeout() throws Exception {
        QuiesceManager manager = new QuiesceManagerImpl(new QuiesceTestContext());
        QuiesceTestBundle bundle = new QuiesceTestBundle();

        manager.quiesce(List.of(bundle));

        assertTrue(bundle.awaitStop());
        assertEquals(Bundle.RESOLVED, bundle.getState());
        assertEquals(1, bundle.stopCount());
    }

    @Test
    void voidRequestStopsBundleWithExplicitTimeout() throws Exception {
        QuiesceManager manager = new QuiesceManagerImpl(new QuiesceTestContext());
        QuiesceTestBundle bundle = new QuiesceTestBundle();

        manager.quiesce(30_000, List.of(bundle));

        assertTrue(bundle.awaitStop());
        assertEquals(Bundle.RESOLVED, bundle.getState());
        assertEquals(1, bundle.stopCount());
    }
}
