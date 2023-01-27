/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.shardingsphere.elasticjob.lite.internal.server;

import org.apache.shardingsphere.elasticjob.infra.handler.sharding.JobInstance;
import org.apache.shardingsphere.elasticjob.lite.internal.schedule.JobRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ServerNodeTest {
    private final ServerNode serverNode = new ServerNode("test_job");

    @BeforeAll
    public static void setUp() {
        JobRegistry.getInstance().addJobInstance("test_job", new JobInstance("127.0.0.1@-@0", null, "127.0.0.1"));
    }

    @Test
    public void assertIsServerPath() {
        assertTrue(serverNode.isServerPath("/test_job/servers/127.0.0.1"));
    }

    @Test
    public void assertIsNotServerPath() {
        assertFalse(serverNode.isServerPath("/test_job/servers/255.255.255.256"));
    }

    @Test
    public void assertIsLocalServerPath() {
        assertTrue(serverNode.isLocalServerPath("/test_job/servers/127.0.0.1"));
    }

    @Test
    public void assertIsNotLocalServerPath() {
        assertFalse(serverNode.isLocalServerPath("/test_job/servers/127.0.0.2"));
    }

    @Test
    public void assertGetServerNode() {
        assertThat(serverNode.getServerNode("127.0.0.1"), is("servers/127.0.0.1"));
    }
}
