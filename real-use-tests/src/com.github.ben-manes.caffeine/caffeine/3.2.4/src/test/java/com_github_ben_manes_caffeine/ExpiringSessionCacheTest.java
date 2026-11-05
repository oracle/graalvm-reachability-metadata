/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Caffeine's time-based expiration subsystem for a session service.
 * An expired session is removed and reported so the service can discard its token.
 */
public class ExpiringSessionCacheTest {
    @Test
    @Timeout(60)
    public void expiresSessionsAndReportsTheExpiredEntry() throws Exception {
        AtomicLong currentTimeNanos = new AtomicLong();
        Ticker ticker = currentTimeNanos::get;
        CountDownLatch expirationObserved = new CountDownLatch(1);
        AtomicReference<Expiration> expiration = new AtomicReference<>();
        Cache<String, Session> sessions = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .ticker(ticker)
                .executor(Runnable::run)
                .removalListener((String key, Session value, RemovalCause cause) -> {
                    expiration.set(new Expiration(key, value, cause));
                    expirationObserved.countDown();
                })
                .build();

        Session activeSession = new Session("user-42", "token-abc");
        sessions.put(activeSession.userId(), activeSession);
        assertSame(activeSession, sessions.getIfPresent(activeSession.userId()));

        currentTimeNanos.addAndGet(Duration.ofMinutes(15).toNanos() + 1);
        sessions.cleanUp();

        assertTrue(expirationObserved.await(10, TimeUnit.SECONDS));
        assertNull(sessions.getIfPresent(activeSession.userId()));
        Expiration observed = expiration.get();
        assertEquals(new Expiration(activeSession.userId(), activeSession, RemovalCause.EXPIRED), observed);
    }

    private record Session(String userId, String token) {
    }

    private record Expiration(String key, Session value, RemovalCause cause) {
    }
}
