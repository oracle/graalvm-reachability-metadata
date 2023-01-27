/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.api.listener.fixture;

import org.apache.shardingsphere.elasticjob.infra.listener.ShardingContexts;
import org.apache.shardingsphere.elasticjob.lite.api.listener.AbstractDistributeOnceElasticJobListener;

@SuppressWarnings("unused")
public final class TestDistributeOnceElasticJobListener extends AbstractDistributeOnceElasticJobListener {
    private final ElasticJobListenerCaller caller;

    public TestDistributeOnceElasticJobListener() {
        this(null);
    }

    public TestDistributeOnceElasticJobListener(final ElasticJobListenerCaller caller) {
        super(1L, 1L);
        this.caller = caller;
    }

    @Override
    public void doBeforeJobExecutedAtLastStarted(final ShardingContexts shardingContexts) {
        caller.before();
    }

    @Override
    public void doAfterJobExecutedAtLastCompleted(final ShardingContexts shardingContexts) {
        caller.after();
    }

    @Override
    public String getType() {
        return "DISTRIBUTE";
    }
}
