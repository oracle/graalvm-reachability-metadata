/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.integrate.disable;

import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.infra.concurrent.BlockUtils;
import org.apache.shardingsphere.elasticjob.lite.internal.schedule.JobRegistry;
import org.apache.shardingsphere.elasticjob.lite.internal.server.ServerStatus;
import org.junit.jupiter.api.Test;
import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job.DetailedFooJob;
import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.integrate.BaseIntegrateTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ScheduleDisabledJobIntegrateTest extends DisabledJobIntegrateTest {
    public ScheduleDisabledJobIntegrateTest() {
        super(BaseIntegrateTest.TestType.SCHEDULE);
    }

    @Override
    protected JobConfiguration getJobConfiguration(final String jobName) {
        return JobConfiguration.newBuilder(jobName, 3).cron("0/1 * * * * ?").shardingItemParameters("0=A,1=B,2=C")
                .jobListenerTypes("INTEGRATE-TEST", "INTEGRATE-DISTRIBUTE").disabled(true).overwrite(true).build();
    }

    @Test
    public void assertJobRunning() {
        BlockUtils.waitingShortTime();
        assertDisabledRegCenterInfo();
        setJobEnable();
        while (!((DetailedFooJob) getElasticJob()).isCompleted()) {
            BlockUtils.waitingShortTime();
        }
        assertEnabledRegCenterInfo();
    }

    private void setJobEnable() {
        BaseIntegrateTest.getREGISTRY_CENTER().persist("/" + getJobName() + "/servers/" + JobRegistry.getInstance().getJobInstance(getJobName()).getServerIp(), ServerStatus.ENABLED.name());
    }

    private void assertEnabledRegCenterInfo() {
        assertTrue(BaseIntegrateTest.getREGISTRY_CENTER().isExisted("/" + getJobName() + "/instances/" + JobRegistry.getInstance().getJobInstance(getJobName()).getJobInstanceId()));
        BaseIntegrateTest.getREGISTRY_CENTER().remove("/" + getJobName() + "/leader/election");
        assertTrue(BaseIntegrateTest.getREGISTRY_CENTER().isExisted("/" + getJobName() + "/sharding"));
    }
}
