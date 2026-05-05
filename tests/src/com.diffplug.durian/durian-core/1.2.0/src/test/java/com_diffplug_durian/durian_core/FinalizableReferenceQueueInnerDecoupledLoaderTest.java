/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {

    private static final String DECOUPLED_LOADER_CLASS_NAME =
            "com.diffplug.common.base.FinalizableReferenceQueue$DecoupledLoader";
    private static final String FINALIZER_CLASS_NAME = "com.diffplug.common.base.internal.Finalizer";

    @Test
    void loadsFinalizerThroughDecoupledClassLoader() throws Exception {
        try {
            Class<?> finalizerClass = loadFinalizerWithDecoupledLoader();

            assertThat(finalizerClass).isNotNull();
            ClassLoader finalizerLoader = finalizerClass.getClassLoader();
            try {
                assertThat(finalizerClass.getName()).isEqualTo(FINALIZER_CLASS_NAME);
                assertThat(finalizerLoader)
                        .isInstanceOf(URLClassLoader.class)
                        .isNotSameAs(ClassLoader.getSystemClassLoader());
            } finally {
                if (finalizerLoader instanceof URLClassLoader urlClassLoader) {
                    urlClassLoader.close();
                }
            }
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
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
}
