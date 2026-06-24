/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {
    private static final String DECOUPLED_LOADER_CLASS_NAME =
            "com.google.common.base.FinalizableReferenceQueue$DecoupledLoader";
    private static final String FINALIZER_CLASS_NAME = "com.google.common.base.internal.Finalizer";

    @Test
    void loadsFinalizerWithDecoupledLoader() throws Exception {
        try {
            Class<?> finalizerClass = loadFinalizerWithDecoupledLoader();
            assertThat(finalizerClass).isNotNull();
            ClassLoader finalizerLoader = finalizerClass.getClassLoader();
            try {
                assertThat(finalizerClass.getName()).isEqualTo(FINALIZER_CLASS_NAME);
            } finally {
                closeIfUrlClassLoader(finalizerLoader);
            }
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedDynamicClassLoading(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    private static Class<?> loadFinalizerWithDecoupledLoader() throws Exception {
        Class<?> decoupledLoaderClass = Class.forName(DECOUPLED_LOADER_CLASS_NAME);
        Constructor<?> constructor = decoupledLoaderClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object decoupledLoader = constructor.newInstance();

        Method loadFinalizer = decoupledLoaderClass.getMethod("loadFinalizer");
        loadFinalizer.setAccessible(true);
        return (Class<?>) loadFinalizer.invoke(decoupledLoader);
    }

    private static void closeIfUrlClassLoader(ClassLoader classLoader) throws Exception {
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            urlClassLoader.close();
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Throwable throwable) {
        if (throwable instanceof Error error) {
            if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw error;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new AssertionError(throwable);
    }
}
