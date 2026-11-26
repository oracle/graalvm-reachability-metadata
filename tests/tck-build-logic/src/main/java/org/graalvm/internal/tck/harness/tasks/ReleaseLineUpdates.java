/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Validates release lines and selects their independent baselines.
/// §FS-library-version-update-automation.1 §AR-test-harness.7
public final class ReleaseLineUpdates {
    private ReleaseLineUpdates() {
    }

    public static boolean matches(String version, String line) {
        return version.equals(line) || version.startsWith(line + ".") || version.startsWith(line + "-");
    }

    static List<String> candidates(String mavenIndex, String library, String baseline, String line,
                                   List<String> tested, List<String> skipped) {
        List<String> versions = FetchExistingLibrariesWithNewerVersionsTask
                .getNewerVersionsFromLibraryIndex(mavenIndex, baseline, library);
        versions.removeIf(version -> tested.contains(version) || skipped.contains(version)
                || (!line.isEmpty() && !matches(version, line)));
        return FetchExistingLibrariesWithNewerVersionsTask.filterPreReleases(versions);
    }

    public static Map<String, String> baselines(List<MetadataVersionsIndexEntry> entries) {
        Map<String, String> baselines = new LinkedHashMap<>();
        for (MetadataVersionsIndexEntry entry : entries) {
            List<String> lines = entry.autoUpdateLines();
            if (lines == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(entry.latest()) || !Boolean.TRUE.equals(entry.autoUpdate())) {
                throw new IllegalArgumentException("auto-update-lines requires latest: true and auto-update: true");
            }
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("auto-update-lines must not be empty");
            }
            List<String> tested = entries.stream().map(MetadataVersionsIndexEntry::testedVersions)
                    .filter(Objects::nonNull).flatMap(List::stream).toList();
            for (String line : lines) {
                if (line == null || !line.matches("[0-9]+(\\.[0-9]+)*")) {
                    throw new IllegalArgumentException("Invalid numeric release line: " + line);
                }
                if (baselines.keySet().stream().anyMatch(other -> matches(line, other) || matches(other, line))) {
                    throw new IllegalArgumentException("Overlapping auto-update-lines: " + line);
                }
                String baseline = tested.stream().filter(version -> matches(version, line))
                        .max(FetchExistingLibrariesWithNewerVersionsTask.VERSION_ORDER)
                        .orElseThrow(() -> new IllegalArgumentException("No tested version for release line " + line));
                baselines.put(line, baseline);
            }
        }
        return baselines;
    }
}
