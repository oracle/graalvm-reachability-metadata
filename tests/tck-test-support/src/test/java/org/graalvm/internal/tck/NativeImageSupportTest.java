/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeImageSupportTest {

    @Test
    void returnsFalseForNull() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(null));
    }

    @Test
    void returnsFalseForOutOfMemoryError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new OutOfMemoryError()));
    }

    @Test
    void returnsFalseForStackOverflowError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new StackOverflowError()));
    }

    @Test
    void detectsNativeImageRuntimeFromSystemProperty() {
        String previousValue = System.getProperty("org.graalvm.nativeimage.imagecode");
        try {
            System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
            assertTrue(NativeImageSupport.isNativeImageRuntime());

            System.setProperty("org.graalvm.nativeimage.imagecode", "buildtime");
            assertFalse(NativeImageSupport.isNativeImageRuntime());
        } finally {
            if (previousValue == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode");
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", previousValue);
            }
        }
    }

    @Test
    void detectsUnsupportedAotClassLoadingFailureAtNativeImageRuntime() {
        String previousValue = System.getProperty("org.graalvm.nativeimage.imagecode");
        try {
            System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
            ClassNotFoundException exception = new ClassNotFoundException("example.Missing");
            exception.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement(
                            "com.oracle.svm.core.hub.registry.AOTClassRegistry",
                            "loadClass",
                            "AOTClassRegistry.java",
                            51)
            });

            assertTrue(NativeImageSupport.isUnsupportedFeatureError(exception));
        } finally {
            if (previousValue == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode");
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", previousValue);
            }
        }
    }

    @Test
    void ignoresAotClassLoadingFailureOutsideNativeImageRuntime() {
        String previousValue = System.getProperty("org.graalvm.nativeimage.imagecode");
        try {
            System.setProperty("org.graalvm.nativeimage.imagecode", "buildtime");
            ClassNotFoundException exception = new ClassNotFoundException("example.Missing");
            exception.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement(
                            "com.oracle.svm.core.hub.registry.AOTClassRegistry",
                            "loadClass",
                            "AOTClassRegistry.java",
                            51)
            });

            assertFalse(NativeImageSupport.isUnsupportedFeatureError(exception));
        } finally {
            if (previousValue == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode");
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", previousValue);
            }
        }
    }

    @Test
    void detectsUnsupportedUrlClassLoaderFailureAtNativeImageRuntime() {
        String previousValue = System.getProperty("org.graalvm.nativeimage.imagecode");
        try {
            System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
            ClassNotFoundException exception = new ClassNotFoundException("example.Missing");
            exception.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement(
                            "java.net.URLClassLoader",
                            "findClass",
                            "URLClassLoader.java",
                            377)
            });

            assertTrue(NativeImageSupport.isUnsupportedFeatureError(exception));
        } finally {
            if (previousValue == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode");
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", previousValue);
            }
        }
    }
}
