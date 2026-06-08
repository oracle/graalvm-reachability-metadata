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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

public class DefaultClassRealmTest {
    private static final String REALM_ID = "foreign-backed";
    private static final String RESOURCE_NAME = "foreign/resource.txt";

    @Test
    void getResourceConsultsForeignClassLoaderWithNormalizedName() throws Exception {
        URL resource = new URL("file:/classworlds/foreign-resource.txt");
        RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader(resource, List.of());
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID, classLoader);

        URL resolved = realm.getResource("/" + RESOURCE_NAME);

        assertThat(resolved).isSameAs(resource);
        assertThat(classLoader.getResourceRequests).containsExactly(RESOURCE_NAME);
    }

    @Test
    void findResourcesIncludesResourcesFromForeignClassLoader() throws Exception {
        URL firstResource = new URL("file:/classworlds/first-resource.txt");
        URL secondResource = new URL("file:/classworlds/second-resource.txt");
        RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader(
                null,
                List.of(firstResource, secondResource));
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID, classLoader);

        List<URL> resources = Collections.list(realm.findResources("/" + RESOURCE_NAME));

        assertThat(resources).containsExactly(firstResource, secondResource);
        assertThat(classLoader.getResourcesRequests).containsExactly(RESOURCE_NAME);
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {
        private final URL resource;
        private final List<URL> resources;
        private final List<String> getResourceRequests = new ArrayList<>();
        private final List<String> getResourcesRequests = new ArrayList<>();

        private RecordingResourceClassLoader(URL resource, List<URL> resources) {
            super(null);
            this.resource = resource;
            this.resources = resources;
        }

        @Override
        public URL getResource(String name) {
            getResourceRequests.add(name);
            if (RESOURCE_NAME.equals(name)) {
                return resource;
            }
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            getResourcesRequests.add(name);
            if (RESOURCE_NAME.equals(name)) {
                return Collections.enumeration(resources);
            }
            return Collections.emptyEnumeration();
        }
    }
}
