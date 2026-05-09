/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.api.uri.UriBuilderImpl;
import java.net.URI;
import javax.ws.rs.Path;
import org.junit.jupiter.api.Test;

public class UriBuilderImplTest {
    @Test
    void appendsPathFromResourceMethodName() {
        final URI uri = new UriBuilderImpl()
                .path(ResourceWithPathMethod.class, "item")
                .build("abc 123");

        assertThat(uri).isEqualTo(URI.create("items/abc%20123"));
    }

    @Path("resources")
    public static final class ResourceWithPathMethod {
        @Path("items/{id}")
        public void item() {
        }
    }
}
