/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.api.listener.fixture;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.elasticjob.infra.listener.ElasticJobListener;
import org.apache.shardingsphere.elasticjob.infra.listener.ShardingContexts;

@SuppressWarnings("unused")
@RequiredArgsConstructor
public final class TestElasticJobListener implements ElasticJobListener {
    private final ElasticJobListenerCaller caller;
    private final String name;
    private final int order;
    private final StringBuilder orderResult;

    public TestElasticJobListener() {
        this(null, null, 0, new StringBuilder());
    }

    @Override
    public void beforeJobExecuted(final ShardingContexts shardingContexts) {
        caller.before();
        orderResult.append(name);
    }

    @Override
    public void afterJobExecuted(final ShardingContexts shardingContexts) {
        caller.after();
        orderResult.append(name);
    }

    @Override
    public String getType() {
        return "TEST";
    }

    @Override
    public int order() {
        return order;
    }
}
