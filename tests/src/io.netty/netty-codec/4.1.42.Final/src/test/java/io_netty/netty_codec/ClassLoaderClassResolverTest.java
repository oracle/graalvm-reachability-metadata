/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassLoaderClassResolverTest {
    private static final String TARGET_CLASS_NAME = "io_netty.netty_codec.ClassLoaderClassResolverTarget";

    @Test
    void resolvesClassesThroughClassLoaderLoadClass() throws Exception {
        RecordingClassLoader classLoader = new RecordingClassLoader(getClass().getClassLoader());
        ClassResolver resolver = ClassResolvers.cacheDisabled(classLoader);

        try {
            Class<?> resolvedClass = resolver.resolve(TARGET_CLASS_NAME);

            assertSame(ClassLoaderClassResolverTarget.class, resolvedClass);
            assertEquals(1, classLoader.loadCount(TARGET_CLASS_NAME));
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void fallsBackToClassForNameWhenLoadClassInitiallyFails() throws Exception {
        FailingOnceClassLoader classLoader = new FailingOnceClassLoader(getClass().getClassLoader(), TARGET_CLASS_NAME);
        ClassResolver resolver = ClassResolvers.cacheDisabled(classLoader);

        try {
            Class<?> resolvedClass = resolver.resolve(TARGET_CLASS_NAME);

            assertSame(ClassLoaderClassResolverTarget.class, resolvedClass);
            assertTrue(classLoader.failedInitialLoad());
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<>();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            recordLoad(name);
            return super.loadClass(name);
        }

        final void recordLoad(String className) {
            loadedClassNames.add(className);
        }

        final int loadCount(String className) {
            int count = 0;
            for (String loadedClassName : loadedClassNames) {
                if (className.equals(loadedClassName)) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class FailingOnceClassLoader extends RecordingClassLoader {
        private final String failingClassName;
        private boolean failed;

        FailingOnceClassLoader(ClassLoader parent, String failingClassName) {
            super(parent);
            this.failingClassName = failingClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (!failed && failingClassName.equals(name)) {
                failed = true;
                recordLoad(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        boolean failedInitialLoad() {
            return failed;
        }
    }
}

class ClassLoaderClassResolverTarget {
}
