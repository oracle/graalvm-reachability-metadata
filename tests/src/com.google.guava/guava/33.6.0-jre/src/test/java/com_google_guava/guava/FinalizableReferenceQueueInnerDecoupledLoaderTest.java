/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.base.FinalizableReferenceQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {
    private static final String DECOUPLED_LOADER_CLASS_NAME =
            "com.google.common.base.FinalizableReferenceQueue$DecoupledLoader";
    private static final String FINALIZER_CLASS_NAME = "com.google.common.base.internal.Finalizer";

    @Test
    void loadsFinalizerThroughDecoupledClassLoader() throws Exception {
        Object decoupledLoader = newDecoupledLoader();
        Method loadFinalizer = decoupledLoader.getClass().getDeclaredMethod("loadFinalizer");
        loadFinalizer.setAccessible(true);

        try {
            Class<?> finalizerClass = (Class<?>) loadFinalizer.invoke(decoupledLoader);

            assertNotNull(finalizerClass);
            assertEquals(FINALIZER_CLASS_NAME, finalizerClass.getName());
            assertNotSame(
                    FinalizableReferenceQueue.class.getClassLoader(), finalizerClass.getClassLoader());
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedDynamicClassLoading(exception.getCause());
        }
    }

    private static Object newDecoupledLoader() throws Exception {
        Class<?> decoupledLoaderClass = Class.forName(DECOUPLED_LOADER_CLASS_NAME);
        Constructor<?> constructor = decoupledLoaderClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
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
