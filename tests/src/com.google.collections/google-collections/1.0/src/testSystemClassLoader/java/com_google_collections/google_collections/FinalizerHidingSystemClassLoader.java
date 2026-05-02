/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public final class FinalizerHidingSystemClassLoader extends URLClassLoader {
    private static final String DECOUPLED_LOADER_TEST_CLASS_NAME =
            "com_google_collections.google_collections."
                    + "FinalizableReferenceQueueInnerDecoupledLoaderTest";
    private static final String FINALIZER_CLASS_NAME =
            "com.google.common.base.internal.Finalizer";

    private static volatile boolean rejectedFinalizerLookup;
    private static volatile boolean resolvedFinalizerLookup;

    public FinalizerHidingSystemClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (FINALIZER_CLASS_NAME.equals(name)) {
            if (calledFromDecoupledLoaderTest()) {
                rejectedFinalizerLookup = true;
                throw new ClassNotFoundException(name);
            }
            Class<?> finalizerClass = super.loadClass(name);
            resolvedFinalizerLookup = true;
            return finalizerClass;
        }
        return super.loadClass(name);
    }

    private static boolean calledFromDecoupledLoaderTest() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (DECOUPLED_LOADER_TEST_CLASS_NAME.equals(stackTraceElement.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void appendToClassPathForInstrumentation(String path) {
        try {
            addURL(new File(path).toURI().toURL());
        } catch (MalformedURLException malformedURLException) {
            throw new IllegalArgumentException("Invalid instrumentation class path: " + path,
                    malformedURLException);
        }
    }

    public static boolean rejectedFinalizerLookup() {
        return rejectedFinalizerLookup;
    }

    public static boolean resolvedFinalizerLookup() {
        return resolvedFinalizerLookup;
    }
}
