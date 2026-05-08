/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import java.net.MalformedURLException;
import java.net.URL;

import org.glassfish.jaxb.core.util.Which;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WhichTest {
    @Test
    void resolvesClassNameThroughProvidedClassLoader() {
        TrackingClassLoader loader = new TrackingClassLoader("file:/virtual/jaxb-core/Which.class");

        String location = Which.which("org.glassfish.jaxb.core.util.Which", loader);

        assertThat(loader.resourceName()).isEqualTo("org/glassfish/jaxb/core/util/Which.class");
        assertThat(location).isEqualTo("file:/virtual/jaxb-core/Which.class");
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private final URL location;
        private String resourceName;

        private TrackingClassLoader(String location) {
            super(null);
            this.location = toUrl(location);
        }

        @Override
        public URL getResource(String name) {
            this.resourceName = name;
            return location;
        }

        private String resourceName() {
            return resourceName;
        }

        private static URL toUrl(String value) {
            try {
                return new URL(value);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid test URL", e);
            }
        }
    }
}
