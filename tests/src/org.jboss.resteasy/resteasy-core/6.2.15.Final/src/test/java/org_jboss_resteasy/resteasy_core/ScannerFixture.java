/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

public class ScannerFixture {
    @ApplicationPath("/indexed")
    public static class IndexedApplication extends Application {
    }

    @Path("/indexed-resource")
    public static class IndexedResource {
        @GET
        public String get() {
            return "indexed";
        }
    }

    @Provider
    public static class IndexedProvider implements ContextResolver<String> {
        @Override
        public String getContext(Class<?> type) {
            return "indexed-context";
        }
    }
}
