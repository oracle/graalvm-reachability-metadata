/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderClassResolverTest {
    @Test
    void cacheDisabledResolverUsesConfiguredClassLoader() throws Exception {
        RecordingClassLoader classLoader = new RecordingClassLoader();
        ClassResolver resolver = ClassResolvers.cacheDisabled(classLoader);

        Class<?> resolvedClass = resolver.resolve(LoadClassTarget.class.getName());

        assertThat(resolvedClass).isSameAs(LoadClassTarget.class);
        assertThat(classLoader.requestedClasses()).contains(LoadClassTarget.class.getName());
    }

    @Test
    void cacheDisabledResolverFallsBackToClassForNameWhenLoadClassMisses() throws Exception {
        String targetClassName = ClassLoaderClassResolverTest.class.getName() + "$FallbackTarget";
        FailingFirstClassLoader classLoader = new FailingFirstClassLoader(targetClassName);
        ClassResolver resolver = ClassResolvers.cacheDisabled(classLoader);

        Class<?> resolvedClass = resolver.resolve(targetClassName);

        assertThat(resolvedClass).isSameAs(FallbackTarget.class);
        assertThat(classLoader.failedLoadClass()).isTrue();
        assertThat(classLoader.requestedClasses()).contains(targetClassName);
    }

    public static final class LoadClassTarget {
    }

    public static final class FallbackTarget {
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedClasses = new ArrayList<>();

        RecordingClassLoader() {
            super(ClassLoaderClassResolverTest.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            recordRequestedClass(name);
            return super.loadClass(name);
        }

        final List<String> requestedClasses() {
            return Collections.unmodifiableList(requestedClasses);
        }

        final void recordRequestedClass(String name) {
            requestedClasses.add(name);
        }
    }

    private static final class FailingFirstClassLoader extends RecordingClassLoader {
        private final String classNameToFailOnce;
        private boolean shouldFail = true;

        FailingFirstClassLoader(String classNameToFailOnce) {
            this.classNameToFailOnce = classNameToFailOnce;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (shouldFail && classNameToFailOnce.equals(name)) {
                shouldFail = false;
                recordRequestedClass(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        final boolean failedLoadClass() {
            return !shouldFail;
        }
    }
}
