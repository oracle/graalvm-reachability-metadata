/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
import org.graalvm.internal.tck.harness.TckExtension;

import javax.inject.Inject;
import java.nio.file.Path;

/**
 * Shared path resolution for README badge summary tasks.
 */
public abstract class AbstractReadmeBadgeSummaryTask extends DefaultTask {

    protected final TckExtension tckExtension;

    @Inject
    public AbstractReadmeBadgeSummaryTask() {
        this.tckExtension = getProject().getExtensions().findByType(TckExtension.class);
    }

    @Internal
    protected Path getRepoRoot() {
        return tckExtension.getRepoRoot().get().getAsFile().toPath();
    }

    @Internal
    protected Path getMetadataRoot() {
        return getRepoRoot().resolve("metadata");
    }

    @Internal
    protected Path getStatsFile() {
        return getRepoRoot().resolve("stats").resolve("stats.json");
    }

    @Internal
    protected Path getOutputRoot() {
        Object property = getProject().findProperty("readmeMetricsOutputRoot");
        if (property != null && !property.toString().isBlank()) {
            return Path.of(property.toString());
        }
        return getProject().getLayout().getBuildDirectory().dir("coverage-stats").get().getAsFile().toPath();
    }

    @Internal
    protected Path getLatestBadgesFile() {
        return getOutputRoot().resolve("latest").resolve("badges.json");
    }

    @Internal
    protected Path getMetricsOverviewGraphFile() {
        return getOutputRoot().resolve("latest").resolve("metrics-over-time.svg");
    }

    @Internal
    protected Path getHistoryFile() {
        return getOutputRoot().resolve("history").resolve("history.json");
    }
}
