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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.strategy.SelfFirstStrategy;
import org.junit.jupiter.api.Test;

public class ForeignStrategyTest {
    private static final String CLASS_NAME = ForeignLoadTarget.class.getName();
    private static final String REALM_ID = "foreign-realm";
    private static final String RESOURCE_NAME = "foreign/resource.txt";

    @Test
    void loadClassConsultsImportedClassLoaderFirst() throws Exception {
        ClassLoader foreignClassLoader = new ClassLoader(
                ForeignStrategyTest.class.getClassLoader()) {
        };
        SelfFirstStrategy strategy = newSelfFirstStrategy(
                foreignClassLoader, ForeignLoadTarget.class.getPackageName());

        Class<?> loadedClass = strategy.loadClass(CLASS_NAME);

        assertThat(loadedClass).isEqualTo(ForeignLoadTarget.class);
    }

    @Test
    void getResourceConsultsImportedClassLoaderFirst() throws Exception {
        RecordingClassLoader foreignClassLoader = new RecordingClassLoader();
        SelfFirstStrategy strategy = newSelfFirstStrategy(foreignClassLoader, "foreign");

        URL resource = strategy.getResource(RESOURCE_NAME);

        assertThat(resource).isEqualTo(foreignClassLoader.resource);
        assertThat(foreignClassLoader.resourceLookups).containsExactly(RESOURCE_NAME);
    }

    @Test
    void getResourcesIncludesResourcesFromImportedClassLoader() throws Exception {
        RecordingClassLoader foreignClassLoader = new RecordingClassLoader();
        SelfFirstStrategy strategy = newSelfFirstStrategy(foreignClassLoader, "foreign");

        List<URL> resources = resources(strategy.getResources(RESOURCE_NAME));

        assertThat(resources).containsExactly(foreignClassLoader.enumeratedResource);
        assertThat(foreignClassLoader.resourcesLookups).containsExactly(RESOURCE_NAME);
    }

    private static SelfFirstStrategy newSelfFirstStrategy(
            ClassLoader foreignClassLoader, String importedPackage) throws Exception {
        ClassWorld world = new ClassWorld();
        ClassRealm realm = world.newRealm(REALM_ID);
        realm.importFrom(foreignClassLoader, importedPackage);
        return new SelfFirstStrategy(realm);
    }

    private static List<URL> resources(Enumeration<?> enumeration) {
        List<URL> resources = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            resources.add((URL) enumeration.nextElement());
        }
        return resources;
    }

    private static URL fileUrl(String path) {
        try {
            return Path.of(path).toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class ForeignLoadTarget {
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
