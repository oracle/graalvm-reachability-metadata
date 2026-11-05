/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's expiration subsystem for a customer session service.
 * Sessions remain available within their configured lifetime and disappear after it.
 */
public class SessionCacheExpiryTest {
    @Test
    @Timeout(60)
    public void expiresSessionsUsingEachSessionLifetime() {
        AtomicLong currentTimeNanos = new AtomicLong();
        Ticker ticker = currentTimeNanos::get;
        Cache<String, Session> sessions = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(Expiry.<String, Session>creating((sessionId, session) ->
                        Duration.ofSeconds(session.lifetimeSeconds())))
                .build();
        Session session = new Session("session-42", "customer-42", 300);
        sessions.put(session.id(), session);

        assertEquals(session, sessions.getIfPresent(session.id()));

        currentTimeNanos.addAndGet(Duration.ofSeconds(299).toNanos());
        assertEquals(session, sessions.getIfPresent(session.id()));

        currentTimeNanos.addAndGet(Duration.ofSeconds(1).toNanos());
        assertNull(sessions.getIfPresent(session.id()));
    }

    private record Session(String id, String customerId, long lifetimeSeconds) {
    }
}
