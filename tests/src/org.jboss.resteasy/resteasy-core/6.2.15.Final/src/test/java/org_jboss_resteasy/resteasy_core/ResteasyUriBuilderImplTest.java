/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.specimpl.ResteasyUriBuilderImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResteasyUriBuilderImplTest {
    @Test
    void appendsResourceMethodPathSelectedByName() {
        String uri = new ResteasyUriBuilderImpl().scheme("https").host("example.test")
                .path(Items.class).path(Items.class, "find").queryParam("view", "full")
                .build("a b").toString();
        assertThat(uri).isEqualTo("https://example.test/items/a%20b?view=full");
    }

    @Path("/items")
    public static class Items {
        @GET
        @Path("/{id}")
        public String find() {
            return "item";
        }
    }
}
