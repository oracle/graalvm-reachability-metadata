/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.tasks.TaskAction;
import org.graalvm.internal.tck.stats.ReadmeBadgeSummarySupport;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Generates the JSON payload consumed by the README shields.io badges.
 */
@SuppressWarnings("unused")
public abstract class GenerateReadmeBadgeSummaryTask extends AbstractReadmeBadgeSummaryTask {

    @TaskAction
    public void generate() {
        LocalDate snapshotDate = LocalDate.now(ZoneOffset.UTC);
        ReadmeBadgeSummarySupport.ExecutionMetricsIndex executionMetricsIndex = ReadmeBadgeSummarySupport.buildExecutionMetricsIndex(
                getStatsRoot(),
                getMetadataRoot(),
                snapshotDate
        );
        ReadmeBadgeSummarySupport.ReadmeBadgeSummary summary = ReadmeBadgeSummarySupport.buildSummary(
                getMetadataRoot(),
                executionMetricsIndex
        );
        ReadmeBadgeSummarySupport.writeSummary(getLatestBadgesFile(), summary);
        ReadmeBadgeSummarySupport.writeExecutionMetrics(getExecutionMetricsRoot(), executionMetricsIndex);

        ReadmeBadgeSummarySupport.ReadmeMetricsHistory history = ReadmeBadgeSummarySupport.loadHistory(getHistoryFile());
        ReadmeBadgeSummarySupport.ReadmeMetricsHistory updatedHistory = ReadmeBadgeSummarySupport.withSnapshot(history, summary);
        ReadmeBadgeSummarySupport.writeHistory(
                getHistoryFile(),
                updatedHistory
        );
        ReadmeBadgeSummarySupport.writeMetricsOverviewGraph(
                getMetricsOverviewGraphFile(),
                updatedHistory
        );
        ReadmeBadgeSummarySupport.writeCoverageMarkdown(
                getCoverageMarkdownFile(),
                summary,
                executionMetricsIndex
        );
        getLogger().quiet("Updated README metrics artifacts under {}.", getOutputRoot());
    }
}
