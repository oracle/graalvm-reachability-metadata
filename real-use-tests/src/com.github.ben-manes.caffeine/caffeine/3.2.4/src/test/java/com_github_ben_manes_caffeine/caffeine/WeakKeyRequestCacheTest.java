/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's weak-key reference subsystem for a request-response service.
 * A response is reused only for the same request object, not for an equal request value.
 */
public class WeakKeyRequestCacheTest {
    @Test
    @Timeout(60)
    public void cachesResponsesByRequestIdentity() {
        Cache<Request, Response> responses = Caffeine.newBuilder()
                .weakKeys()
                .build();
        Request originalRequest = new Request("tenant-42", "/recommendations");
        Request equalRequest = new Request("tenant-42", "/recommendations");
        Response response = new Response(200, "recommendation-17");

        responses.put(originalRequest, response);

        assertEquals(response, responses.getIfPresent(originalRequest));
        assertNull(responses.getIfPresent(equalRequest));
        assertEquals(1, responses.estimatedSize());
        assertEquals(response, responses.getIfPresent(originalRequest));
    }

    private record Request(String tenantId, String path) {
    }

    private record Response(int status, String recommendationId) {
    }
}
