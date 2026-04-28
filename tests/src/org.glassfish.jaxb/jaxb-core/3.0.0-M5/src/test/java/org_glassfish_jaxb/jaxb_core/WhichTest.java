/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.glassfish.jaxb.core.util.Which;
import org.junit.jupiter.api.Test;

public class WhichTest {
    @Test
    public void locatesResourcesThroughProvidedClassLoader() {
        ResourceClassLoader loader = new ResourceClassLoader("example/ResourceBackedType.class");

        String location = Which.which("example.ResourceBackedType", loader);

        assertThat(location).isEqualTo("file:/jaxb-core-test/example/ResourceBackedType.class");
        assertThat(loader.requestedResources()).containsExactly("example/ResourceBackedType.class");
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final List<String> requestedResources = new ArrayList<>();

        private ResourceClassLoader(String resourceName) {
            super(null);
            this.resourceName = resourceName;
        }

        @Override
        protected URL findResource(String name) {
            requestedResources.add(name);
            if (!resourceName.equals(name)) {
                return null;
            }
            try {
                return URI.create("file:/jaxb-core-test/" + name).toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Unable to create test resource URL", e);
            }
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
