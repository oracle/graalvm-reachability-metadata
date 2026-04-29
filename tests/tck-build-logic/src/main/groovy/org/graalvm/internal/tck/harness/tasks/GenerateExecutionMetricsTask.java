/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.stats.ExecutionMetricsSupport;

import javax.inject.Inject;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Generates execution metrics committed under {@code stats/}.
 */
@SuppressWarnings("unused")
public abstract class GenerateExecutionMetricsTask extends DefaultTask {

    private final TckExtension tckExtension;

    @Inject
    public GenerateExecutionMetricsTask() {
        this.tckExtension = getProject().getExtensions().findByType(TckExtension.class);
    }

    @TaskAction
    public void generate() {
        ExecutionMetricsSupport.ExecutionMetricsIndex executionMetricsIndex = ExecutionMetricsSupport.buildIndex(
                getStatsRoot(),
                getMetadataRoot(),
                LocalDate.now(ZoneOffset.UTC)
        );
        ExecutionMetricsSupport.writeExecutionMetrics(getStatsRoot(), executionMetricsIndex);
        getLogger().quiet(
                "Updated execution metrics for {} artifact(s) under {}.",
                executionMetricsIndex.executions().size(),
                getStatsRoot()
        );
    }

    @Internal
    protected Path getMetadataRoot() {
        return getRepoRoot().resolve("metadata");
    }

    @Internal
    protected Path getStatsRoot() {
        return getRepoRoot().resolve("stats");
    }

    @Internal
    protected Path getRepoRoot() {
        return tckExtension.getRepoRoot().get().getAsFile().toPath();
    }
}
