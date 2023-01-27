/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.executor;

import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job.FooJob;
import org.apache.shardingsphere.elasticjob.api.JobConfiguration;
import org.apache.shardingsphere.elasticjob.api.ShardingContext;
import org.apache.shardingsphere.elasticjob.executor.JobFacade;
import org.apache.shardingsphere.elasticjob.executor.item.impl.ClassedJobItemExecutor;

public final class ClassedFooJobExecutor implements ClassedJobItemExecutor<FooJob> {
    @Override
    public void process(final FooJob elasticJob, final JobConfiguration jobConfig, final JobFacade jobFacade, final ShardingContext shardingContext) {
        elasticJob.foo(shardingContext);
    }

    @Override
    public Class<FooJob> getElasticJobClass() {
        return FooJob.class;
    }
}
