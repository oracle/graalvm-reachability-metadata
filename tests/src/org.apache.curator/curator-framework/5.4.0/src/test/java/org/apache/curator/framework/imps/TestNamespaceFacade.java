/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.KeeperException.NoAuthException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("resource")
public class TestNamespaceFacade extends BaseClassForTests {
    @Test
    public void testInvalid() {
        try {
            CuratorFrameworkFactory.builder().namespace("/snafu").retryPolicy(new RetryOneTime(1)).connectString("foo").build();
            fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testGetNamespace() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        CuratorFramework client2 = CuratorFrameworkFactory.builder().namespace("snafu").retryPolicy(new RetryOneTime(1)).connectString("foo").build();
        try {
            client.start();
            CuratorFramework fooClient = client.usingNamespace("foo");
            CuratorFramework barClient = client.usingNamespace("bar");
            assertEquals(client.getNamespace(), "");
            assertEquals(client2.getNamespace(), "snafu");
            assertEquals(fooClient.getNamespace(), "foo");
            assertEquals(barClient.getNamespace(), "bar");
        } finally {
            CloseableUtils.closeQuietly(client2);
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testSimultaneous() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            CuratorFramework fooClient = client.usingNamespace("foo");
            CuratorFramework barClient = client.usingNamespace("bar");
            fooClient.create().forPath("/one");
            barClient.create().forPath("/one");
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/foo/one", false));
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/bar/one", false));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCache() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            assertSame(client.usingNamespace("foo"), client.usingNamespace("foo"));
            assertNotSame(client.usingNamespace("foo"), client.usingNamespace("bar"));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testBasic() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/one");
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/one", false));
            client.usingNamespace("space").create().forPath("/one");
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/space", false));
            client.usingNamespace("name").create().forPath("/one");
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/name", false));
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/name/one", false));
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testRootAccess() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try {
            client.start();
            client.create().forPath("/one");
            assertNotNull(client.getZookeeperClient().getZooKeeper().exists("/one", false));
            assertNotNull(client.checkExists().forPath("/"));
            try {
                client.checkExists().forPath("");
                fail("IllegalArgumentException expected");
            } catch (IllegalArgumentException expected) {
            }
            assertNotNull(client.usingNamespace("one").checkExists().forPath("/"));
            try {
                client.usingNamespace("one").checkExists().forPath("");
                fail("IllegalArgumentException expected");
            } catch (IllegalArgumentException expected) {
            }
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }


    @Test
    public void testIsStarted() {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        CuratorFramework namespaced = client.usingNamespace(null);
        assertEquals(client.getState(), namespaced.getState(), "Namespaced state did not match true state after call to start.");
        client.close();
        assertEquals(client.getState(), namespaced.getState(), "Namespaced state did not match true state after call to close.");
    }

    @Test
    public void testACL() throws Exception {
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        client.getZookeeperClient().blockUntilConnectedOrTimedOut();
        client.create().creatingParentsIfNeeded().forPath("/parent/child", "A string".getBytes());
        CuratorFramework client2 = client.usingNamespace("parent");
        assertNotNull(client2.getData().forPath("/child"));
        client.setACL().withACL(Collections.singletonList(new ACL(ZooDefs.Perms.WRITE, ZooDefs.Ids.ANYONE_ID_UNSAFE))).forPath("/parent/child");
        try {
            List<ACL> acls = client2.getACL().forPath("/child");
            assertNotNull(acls);
            assertEquals(acls.size(), 1);
            assertEquals(acls.get(0).getId(), ZooDefs.Ids.ANYONE_ID_UNSAFE);
            assertEquals(acls.get(0).getPerms(), ZooDefs.Perms.WRITE);
            client2.setACL().withACL(Collections.singletonList(
                            new ACL(ZooDefs.Perms.DELETE, ZooDefs.Ids.ANYONE_ID_UNSAFE))).
                    forPath("/child");
            fail("Expected auth exception was not thrown");
        } catch (NoAuthException ignored) {
        }
    }

    @Test
    public void testUnfixForEmptyNamespace() {
        CuratorFramework client = CuratorFrameworkFactory.builder().namespace("").retryPolicy(new RetryOneTime(1)).connectString("foo").build();
        CuratorFrameworkImpl clientImpl = (CuratorFrameworkImpl) client;
        assertEquals(clientImpl.unfixForNamespace("/foo/bar"), "/foo/bar");
        CloseableUtils.closeQuietly(client);
    }
}
