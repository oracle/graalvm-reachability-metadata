/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.HttpHeaders;

import org.jboss.resteasy.annotations.cache.Cache;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.interceptors.CacheControlFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheControlFeatureTest {
    @Test
    void appliesCachePolicyDeclaredOnResourceInterface() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getProviderFactory().register(CacheControlFeature.class);
            session.dispatcher.getRegistry().addPerRequestResource(CachedResource.class);
            MockHttpResponse response = session.get("/cached");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("cacheable");
            CacheControl policy = CacheControl.valueOf(response.getOutputHeaders().getFirst(HttpHeaders.CACHE_CONTROL).toString());
            assertThat(policy.getMaxAge()).isEqualTo(120);
            assertThat(policy.isPrivate()).isTrue();
        }
    }

    public interface CachedApi {
        @GET
        @Cache(maxAge = 120, isPrivate = true)
        String get();
    }

    @Path("/cached")
    public static class CachedResource implements CachedApi {
        @GET
        @Override
        public String get() {
            return "cacheable";
        }
    }
}
