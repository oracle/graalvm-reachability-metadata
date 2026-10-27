/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises JNA's function-name mapping to select deployment releases using GNU version ordering.
 */
public class NativeReleaseSelectionTest {

    @Test
    @Timeout(60)
    public void selectsTheNewestPublishedReleaseWithinTheRequestedChannel() {
        // Keep application method names independent of the native library's exported symbol names.
        Map<String, String> symbols = Map.of("compareVersions", "strverscmp");
        FunctionMapper mapper = (library, method) -> symbols.getOrDefault(method.getName(), method.getName());
        NativeVersions versions = Native.load(Platform.C_LIBRARY_NAME, NativeVersions.class,
                Map.of(Library.OPTION_FUNCTION_MAPPER, mapper));
        List<Release> manifest = List.of(
                new Release("1.9", "stable", true, "app-1.9.tar"),
                new Release("1.10.2", "stable", true, "app-1.10.2.tar"),
                new Release("2.0", "preview", true, "app-2.0.tar"),
                new Release("1.10.10", "stable", true, "app-1.10.10.tar"),
                new Release("1.11", "stable", false, "app-1.11.tar"),
                new Release("1.10.9", "stable", true, "app-1.10.9.tar"));

        assertEquals(Optional.of("app-1.10.10.tar"), selectArtifact(versions, manifest, "stable"),
                "Numeric version components must outrank lexical order without selecting an unpublished build");
        assertEquals(Optional.of("app-2.0.tar"), selectArtifact(versions, manifest, "preview"),
                "A newer preview must remain isolated from the stable deployment channel");
        assertEquals(Optional.empty(), selectArtifact(versions, manifest, "nightly"),
                "An absent channel must not fall back to an unrelated release");

        List<Release> refreshed = List.of(
                new Release("1.10.10", "stable", true, "app-1.10.10.tar"),
                new Release("1.11", "stable", true, "app-1.11.tar"),
                new Release("1.9", "stable", true, "app-1.9.tar"));
        assertEquals(Optional.of("app-1.11.tar"), selectArtifact(versions, refreshed, "stable"),
                "Refreshing the manifest must select the newly published release rather than retain old results");
    }

    private static Optional<String> selectArtifact(NativeVersions versions, List<Release> manifest, String channel) {
        return manifest.stream()
                .filter(release -> release.published() && release.channel().equals(channel))
                .max((left, right) -> versions.compareVersions(left.version(), right.version()))
                .map(Release::artifact);
    }

    private record Release(String version, String channel, boolean published, String artifact) {
    }

    public interface NativeVersions extends Library {
        int compareVersions(String left, String right);
    }
}
