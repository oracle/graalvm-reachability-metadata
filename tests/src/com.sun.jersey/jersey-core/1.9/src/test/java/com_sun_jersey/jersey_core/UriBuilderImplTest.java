/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.api.uri.UriBuilderImpl;
import java.net.URI;
import javax.ws.rs.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UriBuilderImplTest {
    @Test
    public void appendsPathFromResourceMethodName() {
        final URI uri = new UriBuilderImpl()
                .path("root")
                .path(ItemResource.class, "findItem")
                .build("abc 123");

        assertThat(uri).isEqualTo(URI.create("root/items/abc%20123"));
    }

    public static final class ItemResource {
        @Path("items/{id}")
        public String findItem() {
            return "item";
        }
    }
}
