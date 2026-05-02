/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.OverridingClassLoader;

public class OverridingClassLoaderTest {

    @Test
    void delegatesEligibleClassesToOverrideDelegate() throws Exception {
        ClassLoader parent = OverridingClassLoaderTest.class.getClassLoader();
        TrackingClassLoader overrideDelegate = new TrackingClassLoader(parent);
        OverridingClassLoader classLoader = new OverridingClassLoader(parent, overrideDelegate);
        String className = OverridingClassLoaderTestCandidate.class.getName();

        Class<?> loadedClass;
        try {
            loadedClass = classLoader.loadClass(className);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
            return;
        }

        assertThat(loadedClass).isSameAs(OverridingClassLoaderTestCandidate.class);
        assertThat(overrideDelegate.loadedClassNames).containsExactly(className);
    }

    @Test
    void delegatesExcludedClassesToParentClassLoader() throws Exception {
        OverridingClassLoader classLoader = new OverridingClassLoader(
                OverridingClassLoaderTest.class.getClassLoader()
        );

        Class<?> loadedClass;
        try {
            loadedClass = classLoader.loadClass(String.class.getName());
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
            return;
        }

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    void loadsEligibleClassBytesFromParentClassResource() throws Exception {
        OverridingClassLoader classLoader = new OverridingClassLoader(
                OverridingClassLoaderTest.class.getClassLoader()
        );
        String className = OverridingClassLoaderTestCandidate.class.getName();

        Class<?> loadedClass;
        Class<?> cachedClass;
        try {
            loadedClass = classLoader.loadClass(className);
            cachedClass = classLoader.loadClass(className);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
            return;
        }

        assertThat(loadedClass.getName()).isEqualTo(className);
        assertThat(cachedClass).isSameAs(loadedClass);
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static class TrackingClassLoader extends ClassLoader {

        private final List<String> loadedClassNames = new ArrayList<>();

        TrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            return super.loadClass(name);
        }
    }
}

final class OverridingClassLoaderTestCandidate {

    private OverridingClassLoaderTestCandidate() {
    }
}
