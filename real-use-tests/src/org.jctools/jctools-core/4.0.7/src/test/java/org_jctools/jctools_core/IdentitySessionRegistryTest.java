/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jctools.jctools_core;

import org.jctools.maps.NonBlockingIdentityHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JCTools' identity-keyed map subsystem to track application sessions by object identity.
public class IdentitySessionRegistryTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void tracksDistinctSessionObjectsWithTheSameExternalId() throws Exception {
        NonBlockingIdentityHashMap<Session, String> sessions = new NonBlockingIdentityHashMap<>(2);
        Session checkout = new Session("checkout");
        Session secondCheckout = new Session("checkout");
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRegistration = workers.submit(() -> register(sessions, checkout, workersReady, start));
            Future<?> secondRegistration = workers.submit(
                    () -> register(sessions, secondCheckout, workersReady, start));

            assertTrue(workersReady.await(10, TimeUnit.SECONDS));
            start.countDown();
            firstRegistration.get(10, TimeUnit.SECONDS);
            secondRegistration.get(10, TimeUnit.SECONDS);

            assertEquals(2, sessions.size());
            assertEquals("queued", sessions.get(checkout));
            assertEquals("queued", sessions.get(secondCheckout));
            Session storedCheckout = sessions.keySet().stream()
                    .filter(key -> key == checkout)
                    .findFirst()
                    .orElseThrow();
            assertSame(checkout, storedCheckout);

            assertTrue(sessions.replace(checkout, "queued", "running"));
            assertEquals("running", sessions.get(checkout));
            assertFalse(sessions.remove(new Session("checkout"), "queued"));
            assertTrue(sessions.remove(checkout, "running"));
            assertEquals(1, sessions.size());
            assertEquals("queued", sessions.get(secondCheckout));
        } finally {
            workers.shutdownNow();
        }
    }

    private static void register(NonBlockingIdentityHashMap<Session, String> sessions,
            Session session, CountDownLatch workersReady, CountDownLatch start) {
        workersReady.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The session registry did not start");
            }
            if (sessions.putIfAbsent(session, "queued") != null) {
                throw new IllegalStateException("A session was registered twice");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The session registration was interrupted", exception);
        }
    }

    private record Session(String externalId) {
    }
}
