/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.integrate.enable;

import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job.DetailedFooJob;
import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.infra.concurrent.BlockUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OneOffEnabledJobIntegrateTest extends EnabledJobIntegrateTest {
    public OneOffEnabledJobIntegrateTest() {
        super(TestType.ONE_OFF, new DetailedFooJob());
    }

    @Override
    protected JobConfiguration getJobConfiguration(final String jobName) {
        return JobConfiguration.newBuilder(jobName, 3).shardingItemParameters("0=A,1=B,2=C")
                .jobListenerTypes("INTEGRATE-TEST", "INTEGRATE-DISTRIBUTE").overwrite(true).build();
    }

    @Test
    public void assertJobInit() {
        while (!((DetailedFooJob) getElasticJob()).isCompleted()) {
            BlockUtils.waitingShortTime();
        }
        assertTrue(getREGISTRY_CENTER().isExisted("/" + getJobName() + "/sharding"));
    }
}
