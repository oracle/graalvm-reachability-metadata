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
    private static final String FINALIZER_CLASS_NAME =
            "com.google.common.base.internal.Finalizer";

    private static volatile boolean rejectedFinalizerLookup;

    public FinalizerHidingSystemClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (FINALIZER_CLASS_NAME.equals(name)) {
            rejectedFinalizerLookup = true;
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name);
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
}
