/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class NativeLibraryLoaderTest {
    private static final String LIBRARY_NAME = "netty_resource_probe";
    private static final String ORIGINAL_OS_NAME = System.getProperty("os.name");
    private static final VarHandle STRING_VALUE = stringValueHandle();

    static {
        System.setProperty("os.name", "Mac OS X");
    }

    @AfterAll
    static void restoreOsName() {
        if (ORIGINAL_OS_NAME == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", ORIGINAL_OS_NAME);
        }
    }

    @Test
    void loadSearchesForPackagedNativeLibraryResourcesBeforeFallingBackToSystemLoader() {
        RecordingClassLoader loader = loadWithRecordingClassLoader(LIBRARY_NAME);

        String mappedLibraryResource = nativeResource(System.mapLibraryName(LIBRARY_NAME));
        String macFallbackResource = macFallbackResource(LIBRARY_NAME, mappedLibraryResource);

        Assertions.assertTrue(loader.resourceNames().contains(mappedLibraryResource));
        Assertions.assertTrue(loader.resourceNames().contains(macFallbackResource));
    }

    @Test
    void loadSearchesForDynlibFallbackWhenMappedResourceUsesJniLib() {
        JniLibPathClassLoader loader = new JniLibPathClassLoader();

        Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(LIBRARY_NAME, loader));

        Assertions.assertTrue(loader.resourceNames().contains("META-INF/native/lib" + LIBRARY_NAME + ".dynlib"));
    }

    private static RecordingClassLoader loadWithRecordingClassLoader(String libraryName) {
        RecordingClassLoader loader = new RecordingClassLoader();

        Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(libraryName, loader));

        return loader;
    }

    private static String nativeResource(String mappedLibraryName) {
        return "META-INF/native/" + mappedLibraryName;
    }

    private static String macFallbackResource(String libraryName, String mappedLibraryResource) {
        if (mappedLibraryResource.endsWith(".jnilib")) {
            return "META-INF/native/lib" + libraryName + ".dynlib";
        }
        return "META-INF/native/lib" + libraryName + ".jnilib";
    }

    private static VarHandle stringValueHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
            try {
                return lookup.findVarHandle(String.class, "value", byte[].class);
            } catch (NoSuchFieldException noCompactStrings) {
                return lookup.findVarHandle(String.class, "value", char[].class);
            }
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void forceJniLibSuffix(String value) {
        String suffix = ".jnilib";
        int suffixStart = value.length() - suffix.length();
        Object storage = STRING_VALUE.get(value);
        if (storage instanceof byte[]) {
            byte[] bytes = (byte[]) storage;
            for (int i = 0; i < suffix.length(); i++) {
                bytes[suffixStart + i] = (byte) suffix.charAt(i);
            }
        } else {
            char[] chars = (char[]) storage;
            for (int i = 0; i < suffix.length(); i++) {
                chars[suffixStart + i] = suffix.charAt(i);
            }
        }
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final List<String> resourceNames = new ArrayList<String>();

        private RecordingClassLoader() {
            super(NativeLibraryLoaderTest.class.getClassLoader());
        }

        @Override
        public URL getResource(String name) {
            resourceNames.add(name);
            return null;
        }

        private List<String> resourceNames() {
            return resourceNames;
        }
    }

    private static final class JniLibPathClassLoader extends RecordingClassLoader {
        @Override
        public URL getResource(String name) {
            if (resourceNames().isEmpty()) {
                forceJniLibSuffix(name);
            }
            return super.getResource(name);
        }
    }
}
