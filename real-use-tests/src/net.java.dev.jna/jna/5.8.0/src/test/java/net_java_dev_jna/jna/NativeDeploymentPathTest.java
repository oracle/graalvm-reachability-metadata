/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises JNA's direct native mapping to resolve active deployment paths through POSIX realpath.
 */
public class NativeDeploymentPathTest {

    @Test
    @Timeout(60)
    public void resolvesTheActiveReleaseAfterADeploymentSwitch(@TempDir Path directory) throws IOException {
        Path releases = Files.createDirectory(directory.resolve("releases"));
        Path firstRelease = Files.createDirectory(releases.resolve("release-17"));
        Path nextRelease = Files.createDirectory(releases.resolve("release-18"));
        Files.writeString(firstRelease.resolve("settings.ini"), "revision=17\n");
        Files.writeString(nextRelease.resolve("settings.ini"), "revision=18\n");
        Path active = directory.resolve("current");
        Files.createSymbolicLink(active, directory.relativize(firstRelease));
        Path configuredPath = active.resolve("./settings.ini");

        Path initial = resolveDeploymentPath(configuredPath);
        assertEquals(firstRelease.resolve("settings.ini").toRealPath(), initial,
                "The configured alias must resolve to the first release's canonical file");
        assertEquals("revision=17\n", Files.readString(initial));

        Files.delete(active);
        Files.createSymbolicLink(active, directory.relativize(nextRelease));
        Path updated = resolveDeploymentPath(configuredPath);
        assertEquals(nextRelease.resolve("settings.ini").toRealPath(), updated,
                "Resolving the same configured path must observe the new symlink target");
        assertEquals("revision=18\n", Files.readString(updated));
        assertEquals("revision=17\n", Files.readString(initial),
                "A canonical path retained by an earlier request must still identify its original release");

        assertThrows(IOException.class,
                () -> resolveDeploymentPath(active.resolve("missing.ini")),
                "A missing deployment resource must fail rather than return an unresolved path");
        assertEquals(updated, resolveDeploymentPath(configuredPath),
                "A failed lookup must not prevent a subsequent valid resolution");
    }

    private static Path resolveDeploymentPath(Path configuredPath) throws IOException {
        // A null output buffer asks POSIX realpath to allocate exactly enough native storage.
        Pointer resolved = NativePaths.realpath(configuredPath.toString(), null);
        // realpath may change errno even on success, so only its null result indicates failure.
        if (resolved == null) {
            throw new IOException("Cannot resolve deployment resource: " + configuredPath);
        }
        try {
            return Path.of(resolved.getString(0, Native.getDefaultStringEncoding()));
        } finally {
            NativePaths.free(resolved);
        }
    }

    public static class NativePaths {
        static {
            Native.register(NativePaths.class, Platform.C_LIBRARY_NAME);
        }

        public static native Pointer realpath(String path, Pointer output);

        public static native void free(Pointer allocation);
    }
}
