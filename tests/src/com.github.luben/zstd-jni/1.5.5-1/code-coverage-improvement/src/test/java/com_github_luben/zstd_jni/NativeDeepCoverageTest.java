/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.util.Native;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NativeDeepCoverageTest {
    @Test
    void loadWithTemporaryFolderUsesSystemLibraryEntryWhenOsgiIsPresent(@TempDir Path tempDir) throws Exception {
        assertThat(new org.osgi.framework.BundleEvent()).isNotNull();
        Field loaded = Native.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        boolean previousValue = loaded.getBoolean(null);
        String previousLibraryPath = System.getProperty("java.library.path");
        try {
            installSystemLibrary(tempDir);
            loaded.setBoolean(null, false);
            Native.load(tempDir.toFile());
            assertThat(Native.isLoaded()).isTrue();
        } finally {
            loaded.setBoolean(null, previousValue);
            if (previousLibraryPath == null) {
                System.clearProperty("java.library.path");
            } else {
                System.setProperty("java.library.path", previousLibraryPath);
            }
        }
    }

    private static void installSystemLibrary(Path directory) throws IOException {
        Path library = directory.resolve("libzstd-jni-1.5.5-1.so");
        try (InputStream resource = Native.class.getResourceAsStream(
                "/linux/amd64/libzstd-jni-1.5.5-1.so")) {
            if (resource == null) {
                throw new IOException("The Linux amd64 native library resource is unavailable");
            }
            Files.copy(resource, library);
        }
        System.setProperty("java.library.path", directory.toString());
    }

    @Test
    void noArgumentLoadUsesTheSamePublicOsgiLoadingEntry() throws Exception {
        assertThat(new org.osgi.framework.BundleEvent()).isNotNull();
        Field loaded = Native.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        boolean previousValue = loaded.getBoolean(null);
        try {
            loaded.setBoolean(null, false);
            Native.load();
            assertThat(Native.isLoaded()).isTrue();
        } finally {
            loaded.setBoolean(null, previousValue);
        }
    }
}
