/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import javax.swing.JButton;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FlatInspectorTest {
    private static final String TOOLTIP_BUILDER_CLASS = "com.formdev.flatlaf.extras.FlatInspector";
    private static final String UI_ROW = "UI:</td><td>";

    @Test
    void buildsTooltipUsingPublicJComponentUiMethodOnModernJava() throws Exception {
        try {
            String text = buildToolTipTextWithJavaVersion("21.0.8");

            assertThat(text).contains(UI_ROW);
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }

    @Test
    void buildsTooltipUsingJComponentUiFieldOnLegacyJava() throws Exception {
        try {
            String text = buildToolTipTextWithJavaVersion("1.8.0_402");

            assertThat(text).contains(UI_ROW);
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingFailure(throwable);
        }
    }

    private static String buildToolTipTextWithJavaVersion(String javaVersion) throws Exception {
        String originalJavaVersion = System.getProperty("java.version");
        String originalJavaHome = System.getProperty("java.home");
        try (FlatLafClassLoader classLoader = new FlatLafClassLoader(classPathUrls())) {
            ensureJavaHomeSet();
            System.setProperty("java.version", javaVersion);

            Class<?> inspectorClass = Class.forName(TOOLTIP_BUILDER_CLASS, true, classLoader);
            Method buildToolTipText = inspectorClass.getDeclaredMethod(
                    "buildToolTipText", Component.class, int.class, boolean.class);
            buildToolTipText.setAccessible(true);

            JButton button = new JButton("Inspect");
            button.setSize(button.getPreferredSize());
            return (String) buildToolTipText.invoke(null, button, 0, false);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception nestedException) {
                throw nestedException;
            }
            if (cause instanceof Error nestedError) {
                throw nestedError;
            }
            throw exception;
        } finally {
            restoreProperty("java.version", originalJavaVersion);
            restoreProperty("java.home", originalJavaHome);
        }
    }

    private static URL[] classPathUrls() throws MalformedURLException {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = new File(entries[i]).toURI().toURL();
        }
        return urls;
    }

    private static void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    private static void ensureJavaHomeSet() {
        if (System.getProperty("java.home") != null) {
            return;
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            System.setProperty("java.home", javaHome);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingFailure(Throwable throwable) throws Exception {
        if (hasUnsupportedFeatureError(throwable) || hasUnsupportedIsolatedClassLoadingFailure(throwable)) {
            return;
        }

        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new AssertionError(throwable);
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasUnsupportedIsolatedClassLoadingFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && (message.startsWith("com.formdev.flatlaf.extras.")
                        || message.startsWith("com/formdev/flatlaf/extras/"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class FlatLafClassLoader extends URLClassLoader {
        private FlatLafClassLoader(URL[] urls) {
            super(urls, FlatInspectorTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> cls = findLoadedClass(name);
                if (cls == null) {
                    cls = loadNewClass(name);
                }
                if (resolve) {
                    resolveClass(cls);
                }
                return cls;
            }
        }

        private Class<?> loadNewClass(String name) throws ClassNotFoundException {
            if (name.startsWith("com.formdev.")) {
                return findClass(name);
            }
            return super.loadClass(name, false);
        }
    }
}
