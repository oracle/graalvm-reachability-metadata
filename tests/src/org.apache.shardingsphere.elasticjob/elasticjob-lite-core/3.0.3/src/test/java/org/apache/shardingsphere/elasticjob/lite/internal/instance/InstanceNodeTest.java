/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.shardingsphere.elasticjob.lite.internal.instance;

import org.apache.shardingsphere.elasticjob.infra.handler.sharding.JobInstance;
import org.apache.shardingsphere.elasticjob.lite.internal.schedule.JobRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class InstanceNodeTest {
    private static InstanceNode instanceNode;

    @BeforeAll
    public static void setUp() {
        JobRegistry.getInstance().addJobInstance("test_job", new JobInstance("127.0.0.1@-@0"));
        instanceNode = new InstanceNode("test_job");
    }

    @Test
    public void assertGetInstanceFullPath() {
        assertThat(instanceNode.getInstanceFullPath(), is("/test_job/instances"));
    }

    @Test
    public void assertIsInstancePath() {
        assertTrue(instanceNode.isInstancePath("/test_job/instances/127.0.0.1@-@0"));
    }

    @Test
    public void assertIsNotInstancePath() {
        assertFalse(instanceNode.isInstancePath("/test_job/other/127.0.0.1@-@0"));
    }

    @Test
    public void assertIsLocalInstancePath() {
        assertTrue(instanceNode.isLocalInstancePath("/test_job/instances/127.0.0.1@-@0"));
    }

    @Test
    public void assertIsNotLocalInstancePath() {
        assertFalse(instanceNode.isLocalInstancePath("/test_job/instances/127.0.0.2@-@0"));
    }

    @Test
    public void assertGetLocalInstancePath() {
        assertThat(instanceNode.getLocalInstancePath(), is("instances/127.0.0.1@-@0"));
    }

    @Test
    public void assertGetInstancePath() {
        assertThat(instanceNode.getInstancePath("127.0.0.1@-@0"), is("instances/127.0.0.1@-@0"));
    }
}
