/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.setup;

import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job.DetailedFooJob;
import org_apache_shardingsphere_elasticjob.elasticjob_lite_core.fixture.job.FooJob;
import org.apache.shardingsphere.elasticjob.lite.internal.setup.DefaultJobClassNameProvider;
import org.apache.shardingsphere.elasticjob.lite.internal.setup.JobClassNameProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public final class DefaultJobClassNameProviderTest {
    @Test
    public void assertGetOrdinaryClassJobName() {
        JobClassNameProvider jobClassNameProvider = new DefaultJobClassNameProvider();
        String result = jobClassNameProvider.getJobClassName(new DetailedFooJob());
        assertThat(result, is(DetailedFooJob.class.getName()));
    }

    @Test
    public void assertGetLambdaJobName() {
        JobClassNameProvider jobClassNameProvider = new DefaultJobClassNameProvider();
        FooJob lambdaFooJob = shardingContext -> {
        };
        String result = jobClassNameProvider.getJobClassName(lambdaFooJob);
        assertThat(result, is("org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.setup.DefaultJobClassNameProviderTest$$Lambda$"));
    }
}
