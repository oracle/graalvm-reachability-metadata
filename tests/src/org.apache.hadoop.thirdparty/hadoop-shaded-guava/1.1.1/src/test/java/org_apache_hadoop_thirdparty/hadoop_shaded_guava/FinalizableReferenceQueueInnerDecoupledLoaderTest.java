/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import org.apache.hadoop.thirdparty.com.google.common.base.FinalizableReferenceQueue;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {
    private static final String FINALIZABLE_REFERENCE_QUEUE_CLASS_NAME =
            "org.apache.hadoop.thirdparty.com.google.common.base.FinalizableReferenceQueue";
    private static final String SYSTEM_LOADER_CLASS_NAME = "org.apache.hadoop.thirdparty."
            + "com.google.common.base.FinalizableReferenceQueue$SystemLoader";

    @Test
    void finalizableReferenceQueueUsesDecoupledLoaderWhenSystemLoaderIsDisabled()
            throws Exception {
        try {
            URL guavaLocation = FinalizableReferenceQueue.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            assertThat(instantiateQueueFromIsolatedLoader(guavaLocation))
                    .isEqualTo(FINALIZABLE_REFERENCE_QUEUE_CLASS_NAME);
        } catch (Exception exception) {
            if (!hasExpectedNativeImageClassLoadingFailure(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String instantiateQueueFromIsolatedLoader(URL guavaLocation) throws Exception {
        URL testClassesLocation = FinalizableReferenceQueueInnerDecoupledLoaderTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();

        try (URLClassLoader isolatedLoader = new URLClassLoader(
                new URL[] {testClassesLocation, guavaLocation}, null)) {
            disableSystemLoaderIn(isolatedLoader);
            Class<?> exerciseClass = Class.forName(
                    FinalizableReferenceQueueDecoupledLoaderExercise.class.getName(),
                    true,
                    isolatedLoader);
            Constructor<?> constructor = exerciseClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Callable<?> exercise = (Callable<?>) constructor.newInstance();
            return (String) exercise.call();
        }
    }

    private static void disableSystemLoaderIn(ClassLoader classLoader) throws Exception {
        Class<?> systemLoaderClass = Class.forName(SYSTEM_LOADER_CLASS_NAME, false, classLoader);
        Field disabled = systemLoaderClass.getDeclaredField("disabled");
        disabled.setAccessible(true);
        disabled.setBoolean(null, true);
    }

    private static boolean hasExpectedNativeImageClassLoadingFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException
                    && isExpectedMissingClass(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isExpectedMissingClass(String className) {
        return FINALIZABLE_REFERENCE_QUEUE_CLASS_NAME.equals(className)
                || SYSTEM_LOADER_CLASS_NAME.equals(className)
                || FinalizableReferenceQueueDecoupledLoaderExercise.class.getName().equals(className);
    }
}

final class FinalizableReferenceQueueDecoupledLoaderExercise implements Callable<String> {
    @Override
    public String call() {
        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            return queue.getClass().getName();
        }
    }
}
