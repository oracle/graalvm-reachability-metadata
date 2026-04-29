/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ByteArrayClassLoader;

public class ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava7CapableVmTest {

    @Test
    void invokesClassLoaderLockMethodThroughJava7SynchronizationStrategy() throws Exception {
        String typeName = "org_modelmapper.modelmapper.generated.SynchronizedLockType";
        ExposedByteArrayClassLoader classLoader = new ExposedByteArrayClassLoader(isolatedParent());
        ExposedByteArrayClassLoader.Java7SynchronizationStrategy strategy =
            ExposedByteArrayClassLoader.java7SynchronizationStrategy(classLoadingLockMethod());
        strategy.initializeStrategy();

        Object classLoadingLock = strategy.classLoadingLock(classLoader, typeName);
        Object repeatedClassLoadingLock = strategy.classLoadingLock(classLoader, typeName);

        assertThat(classLoadingLock).isNotNull();
        assertThat(repeatedClassLoadingLock).isSameAs(classLoadingLock);
    }

    private static Method classLoadingLockMethod() throws NoSuchMethodException {
        return ExposedByteArrayClassLoader.class.getMethod("getClassLoadingLock", String.class);
    }

    private static ClassLoader isolatedParent() {
        return new IsolatedParentClassLoader(
            ByteArrayClassLoaderInnerSynchronizationStrategyInnerForJava7CapableVmTest.class.getClassLoader());
    }

    public static final class ExposedByteArrayClassLoader extends ByteArrayClassLoader {
        ExposedByteArrayClassLoader(ClassLoader parent) {
            super(parent, false, Collections.emptyMap());
        }

        @Override
        public Object getClassLoadingLock(String name) {
            return super.getClassLoadingLock(name);
        }

        static Java7SynchronizationStrategy java7SynchronizationStrategy(Method method) {
            return new Java7SynchronizationStrategy(method);
        }

        public static final class Java7SynchronizationStrategy extends SynchronizationStrategy.ForJava7CapableVm {
            Java7SynchronizationStrategy(Method method) {
                super(method);
            }

            void initializeStrategy() {
                initialize();
            }

            Object classLoadingLock(ByteArrayClassLoader classLoader, String name) {
                return getClassLoadingLock(classLoader, name);
            }
        }
    }

    private static final class IsolatedParentClassLoader extends ClassLoader {
        IsolatedParentClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
