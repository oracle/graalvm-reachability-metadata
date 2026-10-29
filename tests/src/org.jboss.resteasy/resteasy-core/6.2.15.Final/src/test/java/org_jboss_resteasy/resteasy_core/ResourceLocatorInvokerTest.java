/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceLocatorInvokerTest {
    @Test
    void locatesSubresourceAndDispatchesToItsMethod() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(Catalog.class);
            MockHttpResponse response = session.get("/catalog/17/details");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("details:17");
            assertThat(session.get("/catalog/23/details").getContentAsString()).isEqualTo("details:23");
        }
    }

    @Path("/catalog")
    public static class Catalog {
        @Path("/{id}")
        public Item locate(@PathParam("id") int id) {
            return new Item(id);
        }
    }

    public static class Item {
        private final int id;

        public Item(int id) {
            this.id = id;
        }

        @GET
        @Path("/details")
        public String get() {
            return "details:" + id;
        }
    }
}
