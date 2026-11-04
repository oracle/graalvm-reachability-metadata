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
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's variable-expiration policy subsystem for an API token service.
 * The service gives tokens different lifetimes and removes each token when its policy expires it.
 */
public class ApiTokenExpirationPolicyTest {
    @Test
    @Timeout(60)
    public void appliesDifferentLifetimesToApiTokens() {
        AtomicLong currentTimeNanos = new AtomicLong();
        Ticker ticker = currentTimeNanos::get;
        Cache<String, ApiToken> tokens = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(Expiry.creating((tokenId, token) -> Duration.ofHours(1)))
                .build();
        Policy.VarExpiration<String, ApiToken> expiration = tokens.policy()
                .expireVariably()
                .orElseThrow();
        ApiToken standard = new ApiToken("token-standard", "read");
        ApiToken privileged = new ApiToken("token-privileged", "read:write");
        tokens.put(standard.id(), standard);
        tokens.put(privileged.id(), privileged);

        expiration.setExpiresAfter(standard.id(), Duration.ofSeconds(30));
        expiration.setExpiresAfter(privileged.id(), Duration.ofSeconds(90));

        assertEquals(Duration.ofSeconds(30), expiration.getExpiresAfter(standard.id()).orElseThrow());
        assertEquals(Duration.ofSeconds(90), expiration.getExpiresAfter(privileged.id()).orElseThrow());
        assertEquals(standard, tokens.getIfPresent(standard.id()));
        assertEquals(privileged, tokens.getIfPresent(privileged.id()));

        currentTimeNanos.addAndGet(Duration.ofSeconds(29).toNanos());
        assertEquals(standard, tokens.getIfPresent(standard.id()));
        assertEquals(privileged, tokens.getIfPresent(privileged.id()));

        currentTimeNanos.addAndGet(Duration.ofSeconds(1).toNanos());
        assertNull(tokens.getIfPresent(standard.id()));
        assertEquals(privileged, tokens.getIfPresent(privileged.id()));

        currentTimeNanos.addAndGet(Duration.ofSeconds(60).toNanos());
        assertNull(tokens.getIfPresent(privileged.id()));
        tokens.cleanUp();
        assertEquals(0, tokens.estimatedSize());
    }

    private record ApiToken(String id, String scope) {
    }
}
