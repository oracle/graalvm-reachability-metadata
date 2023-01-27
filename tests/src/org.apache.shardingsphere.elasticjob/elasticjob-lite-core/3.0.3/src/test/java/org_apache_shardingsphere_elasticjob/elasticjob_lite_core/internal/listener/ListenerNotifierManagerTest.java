/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_shardingsphere_elasticjob.elasticjob_lite_core.internal.listener;

import org.apache.shardingsphere.elasticjob.lite.internal.listener.ListenerNotifierManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class ListenerNotifierManagerTest {

    @Test
    public void assertRegisterAndGetJobNotifyExecutor() {
        String jobName = "test_job";
        ListenerNotifierManager.getInstance().registerJobNotifyExecutor(jobName);
        assertThat(ListenerNotifierManager.getInstance().getJobNotifyExecutor(jobName), notNullValue(Executor.class));
    }

    @Test
    public void assertRemoveAndShutDownJobNotifyExecutor() {
        String jobName = "test_job";
        ListenerNotifierManager.getInstance().registerJobNotifyExecutor(jobName);
        ListenerNotifierManager.getInstance().removeJobNotifyExecutor(jobName);
    }
}
