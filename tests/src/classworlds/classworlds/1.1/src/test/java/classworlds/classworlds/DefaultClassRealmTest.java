/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

public class DefaultClassRealmTest {
    @Test
    void getResourceConsultsForeignClassLoader() throws Exception {
        URL resource = resourceUrl("resource-from-foreign-loader");
        ResourceRecordingClassLoader classLoader = new ResourceRecordingClassLoader(resource);
        ClassRealm realm = new ClassWorld().newRealm("app", classLoader);

        URL resolved = realm.getResource("/config/settings.properties");

        assertThat(resolved).isSameAs(resource);
        assertThat(classLoader.requestedSingleResources).containsExactly("config/settings.properties");
    }

    @Test
    void findResourcesIncludesResourcesFromForeignClassLoader() throws Exception {
        URL firstResource = resourceUrl("first-resource-from-foreign-loader");
        URL secondResource = resourceUrl("second-resource-from-foreign-loader");
        ResourceRecordingClassLoader classLoader = new ResourceRecordingClassLoader(firstResource, secondResource);
        ClassRealm realm = new ClassWorld().newRealm("app", classLoader);

        List<URL> resources = Collections.list(realm.findResources("/META-INF/services/example.Service"));

        assertThat(resources).containsExactly(firstResource, secondResource);
        assertThat(classLoader.requestedResourceEnumerations).containsExactly("META-INF/services/example.Service");
    }

    private static URL resourceUrl(String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new URL(null, "memory:" + content, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                        connected = true;
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(bytes);
                    }
                };
            }
        });
    }

    private static final class ResourceRecordingClassLoader extends ClassLoader {
        private final URL singleResource;
        private final List<URL> resourceEnumeration;
        private final List<String> requestedSingleResources = new ArrayList<>();
        private final List<String> requestedResourceEnumerations = new ArrayList<>();

        private ResourceRecordingClassLoader(URL... resources) {
            super(null);
            singleResource = resources.length == 0 ? null : resources[0];
            resourceEnumeration = List.of(resources);
        }

        @Override
        public URL getResource(String name) {
            requestedSingleResources.add(name);
            return singleResource;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            requestedResourceEnumerations.add(name);
            return Collections.enumeration(resourceEnumeration);
        }
    }
}
