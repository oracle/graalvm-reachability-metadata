/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.integrate.listener;

import org.apache.shardingsphere.elasticjob.infra.listener.ShardingContexts;
import org.apache.shardingsphere.elasticjob.lite.api.listener.AbstractDistributeOnceElasticJobListener;

public class TestDistributeOnceElasticJobListener extends AbstractDistributeOnceElasticJobListener {
    public TestDistributeOnceElasticJobListener() {
        super(100L, 100L);
    }

    @Override
    public void doBeforeJobExecutedAtLastStarted(final ShardingContexts shardingContexts) {
    }

    @Override
    public void doAfterJobExecutedAtLastCompleted(final ShardingContexts shardingContexts) {
    }

    @Override
    public String getType() {
        return "INTEGRATE-DISTRIBUTE";
    }
}
