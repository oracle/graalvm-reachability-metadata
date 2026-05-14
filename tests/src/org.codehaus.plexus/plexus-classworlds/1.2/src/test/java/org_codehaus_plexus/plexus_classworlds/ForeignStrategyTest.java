/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.strategy.ForeignStrategy;
import org.junit.jupiter.api.Test;

public class ForeignStrategyTest {
    private static final String REALM_ID = "foreign-realm";
    private static final String RESOURCE_NAME = "foreign/resource.txt";

    @Test
    void getResourceConsultsForeignClassLoaderFirst() throws Exception {
        RecordingClassLoader foreignClassLoader = new RecordingClassLoader();
        ClassRealm realm = newRealm(foreignClassLoader);

        URL resource = realm.getResource("/" + RESOURCE_NAME);

        assertThat(realm.getStrategy()).isInstanceOf(ForeignStrategy.class);
        assertThat(resource).isEqualTo(foreignClassLoader.resource);
        assertThat(foreignClassLoader.resourceLookups).containsExactly(RESOURCE_NAME);
    }

    @Test
    void findResourcesIncludesResourcesFromForeignClassLoader() throws Exception {
        RecordingClassLoader foreignClassLoader = new RecordingClassLoader();
        ClassRealm realm = newRealm(foreignClassLoader);

        List<URL> resources = resources(realm.findResources("/" + RESOURCE_NAME));

        assertThat(resources).containsExactly(foreignClassLoader.enumeratedResource);
        assertThat(foreignClassLoader.resourcesLookups).containsExactly(RESOURCE_NAME);
    }

    private static ClassRealm newRealm(ClassLoader foreignClassLoader) throws Exception {
        ClassWorld world = new ClassWorld();
        return world.newRealm(REALM_ID, foreignClassLoader);
    }

    private static List<URL> resources(Enumeration enumeration) {
        List<URL> resources = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            resources.add((URL) enumeration.nextElement());
        }
        return resources;
    }

    private static URL fileUrl(String path) {
        try {
            return new URL("file", "", path);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final URL resource = fileUrl("/foreign-resource.txt");
        private final URL enumeratedResource = fileUrl("/foreign-enumerated-resource.txt");
        private final List<String> resourceLookups = new ArrayList<>();
        private final List<String> resourcesLookups = new ArrayList<>();

        private RecordingClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            resourceLookups.add(name);
            if (RESOURCE_NAME.equals(name)) {
                return resource;
            }
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            resourcesLookups.add(name);
            if (RESOURCE_NAME.equals(name)) {
                return Collections.enumeration(Collections.singletonList(enumeratedResource));
            }
            return Collections.emptyEnumeration();
        }
    }
}
