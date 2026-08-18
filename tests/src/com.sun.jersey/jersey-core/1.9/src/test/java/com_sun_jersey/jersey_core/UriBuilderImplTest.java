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
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UriBuilderImplTest {
    @Test
    void appendsPathFromPublicResourceMethodName() {
        RuntimeDelegate.setInstance(new UriBuilderRuntimeDelegate());
        try {
            URI uri = UriBuilder.fromUri("http://example.test/api")
                    .path(ResourceWithMethodPath.class, "item")
                    .build("42");

            assertThat(uri).hasToString("http://example.test/api/items/42");
        } finally {
            RuntimeDelegate.setInstance(null);
        }
    }

    @Path("resources")
    public static final class ResourceWithMethodPath {
        @Path("items/{id}")
        public String item() {
            return "item";
        }
    }

    private static final class UriBuilderRuntimeDelegate extends RuntimeDelegate {
        @Override
        public UriBuilder createUriBuilder() {
            return new UriBuilderImpl();
        }

        @Override
        public ResponseBuilder createResponseBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VariantListBuilder createVariantListBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T createEndpoint(Application application, Class<T> endpointType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            throw new UnsupportedOperationException();
        }
    }
}
