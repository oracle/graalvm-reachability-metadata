/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

public class DefaultClassRealmTest {
    private static final String REALM_ID = "foreign";
    private static final String RESOURCE_NAME = "foreign/resource.txt";

    @Test
    void getResourceDelegatesToForeignClassLoader() throws Exception {
        URL resourceUrl = memoryUrl("memory:foreign-resource");
        ForeignResourceClassLoader foreignClassLoader = new ForeignResourceClassLoader(
                RESOURCE_NAME,
                Collections.singletonList(resourceUrl));
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID, foreignClassLoader);

        URL resource = realm.getResource("/" + RESOURCE_NAME);

        assertThat(resource).isSameAs(resourceUrl);
        assertThat(foreignClassLoader.getRequestedSingleResources()).containsExactly(RESOURCE_NAME);
        assertThat(foreignClassLoader.getRequestedResourceEnumerations()).isEmpty();
    }

    @Test
    void findResourcesIncludesResourcesFromForeignClassLoader() throws Exception {
        URL firstResourceUrl = memoryUrl("memory:foreign-resource-one");
        URL secondResourceUrl = memoryUrl("memory:foreign-resource-two");
        ForeignResourceClassLoader foreignClassLoader = new ForeignResourceClassLoader(
                RESOURCE_NAME,
                List.of(firstResourceUrl, secondResourceUrl));
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID, foreignClassLoader);

        Enumeration<?> resources = realm.findResources("/" + RESOURCE_NAME);
        List<?> resourceList = Collections.list(resources);

        assertThat(resourceList).hasSize(2);
        assertThat(resourceList.get(0)).isSameAs(firstResourceUrl);
        assertThat(resourceList.get(1)).isSameAs(secondResourceUrl);
        assertThat(foreignClassLoader.getRequestedResourceEnumerations()).containsExactly(RESOURCE_NAME);
        assertThat(foreignClassLoader.getRequestedSingleResources()).isEmpty();
    }

    private static URL memoryUrl(String spec) throws IOException {
        return new URL(null, spec, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                        connected = true;
                    }
                };
            }
        });
    }

    private static final class ForeignResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final List<URL> resourceUrls;
        private final List<String> requestedSingleResources = new ArrayList<>();
        private final List<String> requestedResourceEnumerations = new ArrayList<>();

        private ForeignResourceClassLoader(String resourceName, List<URL> resourceUrls) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrls = resourceUrls;
        }

        @Override
        public URL getResource(String name) {
            requestedSingleResources.add(name);
            if (resourceName.equals(name) && !resourceUrls.isEmpty()) {
                return resourceUrls.get(0);
            }
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceEnumerations.add(name);
            if (resourceName.equals(name)) {
                return Collections.enumeration(resourceUrls);
            }
            return Collections.emptyEnumeration();
        }

        private List<String> getRequestedSingleResources() {
            return Collections.unmodifiableList(requestedSingleResources);
        }

        private List<String> getRequestedResourceEnumerations() {
            return Collections.unmodifiableList(requestedResourceEnumerations);
        }
    }
}
