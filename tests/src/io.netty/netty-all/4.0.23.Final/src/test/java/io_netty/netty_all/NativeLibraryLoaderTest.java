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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class NativeLibraryLoaderTest {
    private static final String LIBRARY_NAME = "netty_resource_probe";
    private static final String JNI_STYLE_LIBRARY_NAME = "netty_resource_probe.jnilib";
    private static final String ORIGINAL_OS_NAME = System.getProperty("os.name");

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
    void loadSearchesForDynlibFallbackWhenNativeMappingUsesJniLib() {
        RecordingClassLoader loader = loadWithRecordingClassLoader(JNI_STYLE_LIBRARY_NAME);

        String mappedLibraryResource = nativeResource(System.mapLibraryName(JNI_STYLE_LIBRARY_NAME));
        String macFallbackResource = macFallbackResource(JNI_STYLE_LIBRARY_NAME, mappedLibraryResource);

        Assertions.assertTrue(loader.resourceNames().contains(mappedLibraryResource));
        Assertions.assertTrue(loader.resourceNames().contains(macFallbackResource));
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

    private static final class RecordingClassLoader extends ClassLoader {
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
}
