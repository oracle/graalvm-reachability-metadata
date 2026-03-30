/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.tasks.TaskAction;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.graalvm.internal.tck.stats.LibraryStatsSchemaValidator;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates library stats and stores them in a single artifact-indexed stats file.
 */
@SuppressWarnings("unused")
public abstract class GenerateLibraryStatsTask extends AbstractLibraryStatsTask {

    @TaskAction
    public void generate() {
        List<String> coordinates = resolveRequestedCoordinates();
        Set<StatsLocation> fullyCoveredMetadataVersions = metadataVersionsWithCompleteCoverage(coordinates);
        Map<StatsLocation, List<LibraryStatsModels.VersionStats>> updatesByLocation = collectUpdatesByLocation(coordinates);

        Path statsFile = getStatsFile();
        LibraryStatsModels.LibraryStats libraryStats = LibraryStatsSupport.loadStats(statsFile);

        for (Map.Entry<StatsLocation, List<LibraryStatsModels.VersionStats>> entry : updatesByLocation.entrySet()) {
            StatsLocation location = entry.getKey();
            LibraryStatsModels.MetadataVersionStats existingMetadataVersionStats = LibraryStatsSupport.metadataVersionStats(
                    libraryStats,
                    location.artifact(),
                    location.artifactId(),
                    location.metadataVersion()
            );
            LibraryStatsModels.MetadataVersionStats mergedMetadataVersionStats = LibraryStatsSupport.mergeStats(
                    existingMetadataVersionStats,
                    entry.getValue(),
                    fullyCoveredMetadataVersions.contains(location)
            );
            libraryStats = LibraryStatsSupport.withMetadataVersionStats(
                    libraryStats,
                    location.artifact(),
                    location.artifactId(),
                    location.metadataVersion(),
                    mergedMetadataVersionStats
            );
        }

        LibraryStatsSupport.writeStats(statsFile, libraryStats);
        LibraryStatsSchemaValidator.validateOrThrow(statsFile, getStatsSchemaFile());
        getLogger().quiet("Updated library stats for {} coordinate(s) in {}.", coordinates.size(), statsFile);
    }

    private Map<StatsLocation, List<LibraryStatsModels.VersionStats>> collectUpdatesByLocation(List<String> coordinates) {
        Map<StatsLocation, List<LibraryStatsModels.VersionStats>> updatesByLocation = new LinkedHashMap<>();
        for (String coordinate : coordinates) {
            StatsLocation location = resolveStatsLocation(coordinate);
            LibraryStatsModels.VersionStats update = computeVersionStats(coordinate);
            updatesByLocation.computeIfAbsent(location, ignored -> new ArrayList<>()).add(update);
        }
        return updatesByLocation;
    }
}
