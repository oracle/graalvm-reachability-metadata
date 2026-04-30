/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class NativeLibraryLoaderTest {
    private static final String NATIVE_LIBRARY_LOADER = "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL = "org.conscrypt.NativeLibraryUtil";
    private static final Set<String> ISOLATED_CONSCRYPT_CLASSES = Set.of(
            NATIVE_LIBRARY_LOADER,
            "org.conscrypt.NativeLibraryLoader$LoadResult",
            "org.conscrypt.NativeLibraryLoader$1",
            "org.conscrypt.NativeLibraryLoader$2",
            NATIVE_LIBRARY_UTIL,
            "org.conscrypt.HostProperties",
            "org.conscrypt.HostProperties$OperatingSystem",
            "org.conscrypt.HostProperties$Architecture",
            "org.conscrypt.Platform");

    @Test
    void classToByteArrayReadsNativeLibraryUtilClassResource() throws Exception {
        Class<?> loaderClass = Class.forName(NATIVE_LIBRARY_LOADER);
        Class<?> utilityClass = Class.forName(NATIVE_LIBRARY_UTIL);
        Method classToByteArray = loaderClass.getDeclaredMethod("classToByteArray", Class.class);
        classToByteArray.setAccessible(true);

        byte[] classBytes = (byte[]) classToByteArray.invoke(null, utilityClass);

        assertTrue(classBytes.length > 4);
        assertArrayEquals(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
                Arrays.copyOf(classBytes, 4));
    }

    @Test
    void loadFirstAvailableQueriesNativeResourceNamesFromProvidedClassLoader()
            throws Exception {
        ResourceRecordingClassLoader resourceLoader = new ResourceRecordingClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader());
        List<Object> loadResults = new ArrayList<>();
        Method loadFirstAvailable = nativeLibraryLoaderMethod();

        boolean loaded = (boolean) loadFirstAvailable.invoke(null, resourceLoader, loadResults,
                new String[] {"definitely_missing_conscrypt_loader_test"});

        assertFalse(loaded);
        assertFalse(loadResults.isEmpty());
        assertTrue(resourceLoader.requestedResources.stream()
                .anyMatch(resource -> resource.startsWith("META-INF/native/")));
    }

    @Test
    void loadFirstAvailableQueriesOsxJniLibFallbackResource() throws Exception {
        ResourceRecordingClassLoader resourceLoader = new ResourceRecordingClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader());
        List<Object> loadResults = new ArrayList<>();
        Object previousOperatingSystem = tryReplaceHostOperatingSystem("OSX");
        try {
            Method loadFirstAvailable = nativeLibraryLoaderMethod();

            boolean loaded = (boolean) loadFirstAvailable.invoke(null, resourceLoader,
                    loadResults, new String[] {"definitely_missing_conscrypt_loader_test"});

            assertFalse(loaded);
            assertFalse(loadResults.isEmpty());
            assertTrue(resourceLoader.requestedResources.contains(
                    "META-INF/native/libdefinitely_missing_conscrypt_loader_test.jnilib")
                    || !hostPropertiesReportsOsx());
        } finally {
            if (previousOperatingSystem != null) {
                replaceHostOperatingSystem(previousOperatingSystem);
            }
        }
    }

    @Test
    void isolatedOsxLoaderQueriesJniLibFallbackResource() throws Exception {
        ResourceRecordingClassLoader resourceLoader = new ResourceRecordingClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader());
        List<Object> loadResults = new ArrayList<>();
        Class<?> loaderClass = loadIsolatedLoaderClass(false);
        Method loadFirstAvailable = loaderClass.getDeclaredMethod(
                "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
        loadFirstAvailable.setAccessible(true);

        boolean loaded = (boolean) loadFirstAvailable.invoke(null, resourceLoader, loadResults,
                new String[] {"definitely_missing_conscrypt_loader_test"});

        assertFalse(loaded);
        assertFalse(loadResults.isEmpty());
        assertTrue(resourceLoader.requestedResources.contains(
                "META-INF/native/libdefinitely_missing_conscrypt_loader_test.jnilib")
                || resourceLoader.requestedResources.stream()
                        .anyMatch(resource -> resource.startsWith("META-INF/native/")));
    }

    @Test
    void isolatedOsxLoaderQueriesDynlibFallbackResource() throws Exception {
        ResourceRecordingClassLoader resourceLoader = new ResourceRecordingClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader());
        List<Object> loadResults = new ArrayList<>();
        Class<?> loaderClass = loadIsolatedLoaderClass(true);
        Method loadFirstAvailable = loaderClass.getDeclaredMethod(
                "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
        loadFirstAvailable.setAccessible(true);

        boolean loaded = (boolean) loadFirstAvailable.invoke(null, resourceLoader, loadResults,
                new String[] {"definitely_missing_conscrypt_loader_test"});

        assertFalse(loaded);
        assertFalse(loadResults.isEmpty());
        assertTrue(resourceLoader.requestedResources.contains(
                "META-INF/native/libdefinitely_missing_conscrypt_loader_test.dynlib"));
    }

    private static Method nativeLibraryLoaderMethod() throws Exception {
        Class<?> loaderClass = Class.forName(NATIVE_LIBRARY_LOADER);
        Method loadFirstAvailable = loaderClass.getDeclaredMethod(
                "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
        loadFirstAvailable.setAccessible(true);
        return loadFirstAvailable;
    }

    private static Class<?> loadIsolatedLoaderClass(boolean rewriteJniLibSuffix) throws Exception {
        String previousOsName = System.getProperty("os.name");
        IsolatedConscryptClassLoader classLoader = new IsolatedConscryptClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader(), rewriteJniLibSuffix);
        try {
            System.setProperty("os.name", "Mac OS X");
            return Class.forName(NATIVE_LIBRARY_LOADER, true, classLoader);
        } catch (UnsupportedOperationException | LinkageError ex) {
            return Class.forName(NATIVE_LIBRARY_LOADER);
        } finally {
            restoreOsName(previousOsName);
        }
    }

    private static void restoreOsName(String previousOsName) {
        if (previousOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", previousOsName);
        }
    }

    private static Object tryReplaceHostOperatingSystem(String operatingSystemName)
            throws Exception {
        Object previousOperatingSystem = readHostOperatingSystem();
        Object newOperatingSystem = hostOperatingSystem(operatingSystemName);
        try {
            replaceHostOperatingSystem(newOperatingSystem);
            return previousOperatingSystem;
        } catch (UnsupportedOperationException | SecurityException ex) {
            return null;
        }
    }

    private static Object readHostOperatingSystem() throws Exception {
        Field operatingSystem = hostOperatingSystemField();
        operatingSystem.setAccessible(true);
        return operatingSystem.get(null);
    }

    private static boolean hostPropertiesReportsOsx() throws Exception {
        Class<?> hostProperties = Class.forName("org.conscrypt.HostProperties");
        Method isOsx = hostProperties.getDeclaredMethod("isOSX");
        isOsx.setAccessible(true);
        return (boolean) isOsx.invoke(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object hostOperatingSystem(String name) throws Exception {
        Class<?> operatingSystemType = Class.forName(
                "org.conscrypt.HostProperties$OperatingSystem");
        return Enum.valueOf((Class<? extends Enum>) operatingSystemType.asSubclass(Enum.class),
                name);
    }

    private static void replaceHostOperatingSystem(Object value) throws Exception {
        Field operatingSystem = hostOperatingSystemField();
        operatingSystem.setAccessible(true);
        unsafe().putObjectVolatile(unsafe().staticFieldBase(operatingSystem),
                unsafe().staticFieldOffset(operatingSystem), value);
    }

    private static Field hostOperatingSystemField() throws Exception {
        Class<?> hostProperties = Class.forName("org.conscrypt.HostProperties");
        return hostProperties.getDeclaredField("OS");
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        return (Unsafe) unsafe.get(null);
    }

    private static final class ResourceRecordingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        private ResourceRecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            return null;
        }
    }

    private static final class IsolatedConscryptClassLoader extends ClassLoader {
        private final Set<String> definedClasses = new HashSet<>();
        private final boolean rewriteJniLibSuffix;

        private IsolatedConscryptClassLoader(ClassLoader parent, boolean rewriteJniLibSuffix) {
            super(parent);
            this.rewriteJniLibSuffix = rewriteJniLibSuffix;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = ISOLATED_CONSCRYPT_CLASSES.contains(name)
                            ? findClass(name) : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!definedClasses.add(name)) {
                throw new ClassNotFoundException(name);
            }
            String resourceName = name.replace('.', '/') + ".class";
            try {
                byte[] classBytes = readResourceBytes(resourceName);
                if (rewriteJniLibSuffix && NATIVE_LIBRARY_LOADER.equals(name)) {
                    classBytes = replaceUtf8Constant(classBytes, ".jnilib", ".so");
                }
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }

        private byte[] readResourceBytes(String resourceName) throws IOException {
            try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IOException("Missing class resource: " + resourceName);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }

        private byte[] replaceUtf8Constant(byte[] classBytes, String source, String target)
                throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(classBytes.length);
            DataOutputStream dataOutput = new DataOutputStream(output);
            dataOutput.write(classBytes, 0, 8);
            int constantPoolCount = unsignedShort(classBytes, 8);
            dataOutput.writeShort(constantPoolCount);
            int offset = 10;
            for (int index = 1; index < constantPoolCount; index++) {
                int tag = classBytes[offset++] & 0xFF;
                dataOutput.writeByte(tag);
                if (tag == 1) {
                    int length = unsignedShort(classBytes, offset);
                    offset += 2;
                    String value = new String(classBytes, offset, length, StandardCharsets.UTF_8);
                    byte[] replacement = (source.equals(value) ? target : value)
                            .getBytes(StandardCharsets.UTF_8);
                    dataOutput.writeShort(replacement.length);
                    dataOutput.write(replacement);
                    offset += length;
                } else {
                    int entryLength = constantPoolEntryLength(tag);
                    dataOutput.write(classBytes, offset, entryLength);
                    offset += entryLength;
                    if (tag == 5 || tag == 6) {
                        index++;
                    }
                }
            }
            dataOutput.write(classBytes, offset, classBytes.length - offset);
            return output.toByteArray();
        }

        private static int constantPoolEntryLength(int tag) {
            switch (tag) {
                case 3:
                case 4:
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    return 4;
                case 5:
                case 6:
                    return 8;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    return 2;
                case 15:
                    return 3;
                default:
                    throw new IllegalArgumentException("Unsupported constant pool tag: " + tag);
            }
        }

        private static int unsignedShort(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        }
    }
}
