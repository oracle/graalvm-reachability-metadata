/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.scopedpool.ScopedClassPoolRepository;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;

import org.junit.jupiter.api.Test;

public class ScopedClassPoolTest {
    private static final String FIXTURE_CLASS_NAME = ScopedPoolFixture.class.getName();
    private static final String FIXTURE_RESOURCE_NAME = FIXTURE_CLASS_NAME.replace('.', '/') + ".class";

    @Test
    void getChecksClassLoaderResourceBeforeResolvingClass() throws Exception {
        ScopedClassPoolRepository repository = ScopedClassPoolRepositoryImpl.getInstance();
        TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(
                ScopedClassPoolTest.class.getClassLoader());
        ClassPool classPool = repository.registerClassLoader(classLoader);

        try {
            CtClass resolvedClass = classPool.get(FIXTURE_CLASS_NAME);

            assertThat(resolvedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
            assertThat(classLoader.requestedResources()).contains(FIXTURE_RESOURCE_NAME);
        } finally {
            repository.unregisterClassLoader(classLoader);
        }
    }

    public static class ScopedPoolFixture {
    }

    private static class TrackingResourceClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        TrackingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            return super.getResource(name);
        }

        List<String> requestedResources() {
            return requestedResources;
        }
    }
}
