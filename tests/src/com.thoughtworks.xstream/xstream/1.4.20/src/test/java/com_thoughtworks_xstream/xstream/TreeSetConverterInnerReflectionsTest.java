/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import sun.misc.Unsafe;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.SunLimitedUnsafeReflectionProvider;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.Fields;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeSetConverterInnerReflectionsTest {
    @Test
    void restoresTreeSetUsingDefaultOptimizedAddAllDetection() {
        TreeSet<String> original = treeSetOf("bravo", "alpha", "charlie");

        TreeSet<String> restoredSet = roundTripTreeSet(new XStream(), original);

        assertThat(restoredSet.comparator()).isNull();
        assertThat(restoredSet).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void restoresTreeSetWhenOptimizedAddAllIsUnavailable() throws Exception {
        try (ChildFirstClassLoader loader = new ChildFirstClassLoader(classPathUrls())) {
            Class<?> scenarioClass = loader.loadClass(UnoptimizedTreeSetRoundTrip.class.getName());
            Runnable scenario = scenarioClass.asSubclass(Runnable.class).getConstructor().newInstance();

            scenario.run();
        } catch (InvocationTargetException e) {
            throw rethrowCause(e);
        } catch (ClassNotFoundException | UnsupportedOperationException e) {
            TreeSet<String> original = treeSetOf("delta", "alpha", "charlie");

            TreeSet<String> restoredSet = roundTripTreeSet(new XStream(), original);

            assertThat(restoredSet).containsExactly("alpha", "charlie", "delta");
        }
    }

    private static Exception rethrowCause(InvocationTargetException exception) throws Exception {
        Throwable cause = exception.getCause();
        if (cause instanceof Error) {
            throw (Error)cause;
        }
        if (cause instanceof Exception) {
            throw (Exception)cause;
        }
        throw new AssertionError(cause);
    }

    @SuppressWarnings("unchecked")
    private static TreeSet<String> roundTripTreeSet(XStream xstream, TreeSet<String> original) {
        Object restored = xstream.fromXML(xstream.toXML(original));

        assertThat(restored).isInstanceOf(TreeSet.class);
        return (TreeSet<String>)restored;
    }

    private static TreeSet<String> treeSetOf(String... values) {
        TreeSet<String> set = new TreeSet<>();
        set.addAll(Arrays.asList(values));
        return set;
    }

    private static URL[] classPathUrls() throws MalformedURLException {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = new File(entries[i]).toURI().toURL();
        }
        return urls;
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private ChildFirstClassLoader(URL[] urls) {
            super(urls, TreeSetConverterInnerReflectionsTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadIsolatedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadIsolatedClass(String name) throws ClassNotFoundException {
            if (isIsolated(name)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException ignored) {
                    // Delegate below.
                }
            }
            return super.loadClass(name, false);
        }

        private static boolean isIsolated(String name) {
            return name.startsWith("com.thoughtworks.xstream.")
                    || name.startsWith(TreeSetConverterInnerReflectionsTest.class.getName());
        }
    }

    public static final class UnoptimizedTreeSetRoundTrip implements Runnable {
        @Override
        public void run() {
            withOptimizedTreeSetAddAll(false, () -> {
                XStream xstream = new XStream();
                TreeSet<String> original = treeSetOf("delta", "alpha", "charlie");
                Object restored = xstream.fromXML(xstream.toXML(original));

                if (!(restored instanceof TreeSet)) {
                    throw new AssertionError("Expected restored value to be a TreeSet");
                }
                List<?> restoredValues = new ArrayList<>((TreeSet<?>)restored);
                if (!restoredValues.equals(Arrays.asList("alpha", "charlie", "delta"))) {
                    throw new AssertionError("Unexpected TreeSet contents: " + restoredValues);
                }
            });
        }

        private static void withOptimizedTreeSetAddAll(boolean optimized, Runnable action) {
            java.lang.reflect.Field field = Fields.find(JVM.class, "optimizedTreeSetAddAll");
            Unsafe unsafe = UnsafeAccess.unsafe();
            Object base = unsafe.staticFieldBase(field);
            long offset = unsafe.staticFieldOffset(field);
            boolean previous = unsafe.getBoolean(base, offset);
            unsafe.putBoolean(base, offset, optimized);
            try {
                action.run();
            } finally {
                unsafe.putBoolean(base, offset, previous);
            }
        }

        private static TreeSet<String> treeSetOf(String... values) {
            TreeSet<String> set = new TreeSet<>();
            set.addAll(Arrays.asList(values));
            return set;
        }

        private static final class UnsafeAccess extends SunLimitedUnsafeReflectionProvider {
            private static Unsafe unsafe() {
                return unsafe;
            }
        }
    }
}
