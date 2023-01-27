/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@SuppressWarnings("unused")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiteYamlConstants {
    private static final String JOB_YAML = """
            jobName: test_job
            cron: 0/1 * * * * ?
            shardingTotalCount: 3
            jobParameter: 'param'
            failover: %s
            monitorExecution: %s
            misfire: false
            maxTimeDiffSeconds: %s
            reconcileIntervalMinutes: 15
            description: 'desc'
            disabled: true
            overwrite: true""";
    private static final boolean DEFAULT_FAILOVER = true;
    private static final boolean DEFAULT_MONITOR_EXECUTION = true;
    private static final int DEFAULT_MAX_TIME_DIFF_SECONDS = 1000;

    public static String getJobYaml() {
        return String.format(JOB_YAML, DEFAULT_FAILOVER, DEFAULT_MONITOR_EXECUTION, DEFAULT_MAX_TIME_DIFF_SECONDS);
    }

    public static String getJobYaml(final int maxTimeDiffSeconds) {
        return String.format(JOB_YAML, DEFAULT_FAILOVER, DEFAULT_MONITOR_EXECUTION, maxTimeDiffSeconds);
    }

    public static String getJobYamlWithFailover(final boolean failover) {
        return String.format(JOB_YAML, failover, DEFAULT_MONITOR_EXECUTION, DEFAULT_MAX_TIME_DIFF_SECONDS);
    }

    public static String getJobYamlWithMonitorExecution(final boolean monitorExecution) {
        return String.format(JOB_YAML, DEFAULT_FAILOVER, monitorExecution, DEFAULT_MAX_TIME_DIFF_SECONDS);
    }
}
