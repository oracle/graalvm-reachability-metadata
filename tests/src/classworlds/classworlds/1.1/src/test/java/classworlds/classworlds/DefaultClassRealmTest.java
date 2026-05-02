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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultClassRealmTest {
    private static final String REALM_ID = "resource-realm";
    private static final String RESOURCE_NAME = "realm-resource.txt";
    private static final String RESOURCE_REQUEST = "/" + RESOURCE_NAME;

    @TempDir
    Path temporaryDirectory;

    @Test
    void getResourceDelegatesToForeignClassLoader() throws Exception {
        URL resource = createResourceUrl("single-resource.txt");
        ResourceClassLoader foreignClassLoader = new ResourceClassLoader(
                Map.of(RESOURCE_NAME, List.of(resource)));
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID, foreignClassLoader);

        URL locatedResource = realm.getResource(RESOURCE_REQUEST);

        assertThat(locatedResource).isEqualTo(resource);
        assertThat(foreignClassLoader.singleResourceRequests).containsExactly(RESOURCE_NAME);
    }

    @Test
    void findResourcesIncludesResourcesFromForeignClassLoader() throws Exception {
        URL firstResource = createResourceUrl("first-resource.txt");
        URL secondResource = createResourceUrl("second-resource.txt");
        ResourceClassLoader foreignClassLoader = new ResourceClassLoader(
                Map.of(RESOURCE_NAME, List.of(firstResource, secondResource)));
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID, foreignClassLoader);

        Enumeration resources = realm.findResources(RESOURCE_REQUEST);

        assertThat(Collections.list(resources)).contains(firstResource, secondResource);
        assertThat(foreignClassLoader.multiResourceRequests).containsExactly(RESOURCE_NAME);
    }

    private URL createResourceUrl(String fileName) throws IOException {
        Path resourceFile = temporaryDirectory.resolve(fileName);
        Files.writeString(resourceFile, fileName);
        return resourceFile.toUri().toURL();
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, List<URL>> resources;
        private final List<String> singleResourceRequests = new ArrayList<>();
        private final List<String> multiResourceRequests = new ArrayList<>();

        ResourceClassLoader(Map<String, List<URL>> resources) {
            super(DefaultClassRealmTest.class.getClassLoader());
            this.resources = new HashMap<>(resources);
        }

        @Override
        public URL getResource(String name) {
            singleResourceRequests.add(name);
            List<URL> matchingResources = resources.getOrDefault(name, List.of());
            if (matchingResources.isEmpty()) {
                return null;
            }
            return matchingResources.get(0);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            multiResourceRequests.add(name);
            return Collections.enumeration(resources.getOrDefault(name, List.of()));
        }
    }
}
