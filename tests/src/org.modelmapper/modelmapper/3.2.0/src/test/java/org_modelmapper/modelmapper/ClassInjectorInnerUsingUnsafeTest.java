/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.UsingUnsafeAccess;

public class ClassInjectorInnerUsingUnsafeTest {

    @Test
    void returnsAlreadyLoadedTypeWithoutDefiningIt() {
        Map<String, Class<?>> injectedTypes = UsingUnsafeAccess.injectWithThrowingDispatcher(
            getClass().getClassLoader(),
            new AssertionError("Dispatcher should not define an already loaded type"),
            Collections.singletonMap(AlreadyLoadedType.class.getName(), new byte[0]));

        assertThat(injectedTypes)
            .containsEntry(AlreadyLoadedType.class.getName(), AlreadyLoadedType.class);
    }

    @Test
    void resolvesTypeAfterRuntimeExceptionFromDispatcher() {
        String typeName = RuntimeFallbackType.class.getName();
        FailOnceClassLoader classLoader = new FailOnceClassLoader(
            getClass().getClassLoader(),
            typeName);
        Map<String, Class<?>> injectedTypes = UsingUnsafeAccess.injectWithThrowingDispatcher(
            classLoader,
            new IllegalStateException("simulated unsafe definition race"),
            Collections.singletonMap(typeName, new byte[0]));

        assertThat(injectedTypes).containsEntry(typeName, RuntimeFallbackType.class);
        assertThat(classLoader.getRejectedLoadCount()).isEqualTo(1);
    }

    @Test
    void resolvesTypeAfterErrorFromDispatcher() {
        String typeName = ErrorFallbackType.class.getName();
        FailOnceClassLoader classLoader = new FailOnceClassLoader(
            getClass().getClassLoader(),
            typeName);
        Map<String, Class<?>> injectedTypes = UsingUnsafeAccess.injectWithThrowingDispatcher(
            classLoader,
            new LinkageError("simulated unsafe definition linkage race"),
            Collections.singletonMap(typeName, new byte[0]));

        assertThat(injectedTypes).containsEntry(typeName, ErrorFallbackType.class);
        assertThat(classLoader.getRejectedLoadCount()).isEqualTo(1);
    }

    public static final class AlreadyLoadedType {
    }

    public static final class RuntimeFallbackType {
    }

    public static final class ErrorFallbackType {
    }

    private static final class FailOnceClassLoader extends ClassLoader {
        private final String rejectedName;
        private int rejectedLoadCount;

        FailOnceClassLoader(ClassLoader parent, String rejectedName) {
            super(parent);
            this.rejectedName = rejectedName;
        }

        int getRejectedLoadCount() {
            return rejectedLoadCount;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.equals(rejectedName) && rejectedLoadCount == 0) {
                    rejectedLoadCount++;
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}
