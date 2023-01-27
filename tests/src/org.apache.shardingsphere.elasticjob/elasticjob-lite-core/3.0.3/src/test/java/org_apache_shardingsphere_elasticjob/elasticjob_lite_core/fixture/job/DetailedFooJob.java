/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job;

import lombok.Getter;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

public final class DetailedFooJob implements FooJob {

    private final Collection<Integer> completedJobItems = new CopyOnWriteArraySet<>();

    @Getter
    private volatile boolean completed;

    @Override
    public void foo(final ShardingContext shardingContext) {
        completedJobItems.add(shardingContext.getShardingItem());
        completed = completedJobItems.size() == shardingContext.getShardingTotalCount();
    }
}
