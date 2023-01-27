/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.setup;

import org.apache.shardingsphere.elasticjob.lite.internal.setup.DefaultJobClassNameProvider;
import org.apache.shardingsphere.elasticjob.lite.internal.setup.JobClassNameProviderFactory;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;

public final class JobClassNameProviderFactoryTest {
    @Test
    public void assertGetDefaultStrategy() {
        MatcherAssert.assertThat(JobClassNameProviderFactory.getProvider(), instanceOf(DefaultJobClassNameProvider.class));
    }
}
