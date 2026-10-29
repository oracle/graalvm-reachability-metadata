/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.resteasy.annotations.Query;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryInjectorTest {
    @Test
    void constructsQueryObjectAndAppliesDefaults() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(SearchResource.class);
            MockHttpResponse response = session.get("/search?term=metadata");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("metadata:10");
            assertThat(session.get("/search?term=native&limit=3").getContentAsString()).isEqualTo("native:3");
        }
    }

    public static class SearchQuery {
        @QueryParam("term")
        private String term;
        @QueryParam("limit")
        @DefaultValue("10")
        private int limit;
    }

    @Path("/search")
    public static class SearchResource {
        @GET
        public String get(@Query SearchQuery query) {
            return query.term + ":" + query.limit;
        }
    }
}
