/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CreateBuilderMain;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.zookeeper.ZooDefs.Ids.ANYONE_ID_UNSAFE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"FieldMayBeFinal", "deprecation", "unused", "OptionalGetWithoutIsPresent"})
public class TestCreate extends BaseClassForTests {
    private static final List<ACL> READ_CREATE = Collections.singletonList(new ACL(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ, ANYONE_ID_UNSAFE));
    private static final List<ACL> READ_CREATE_WRITE = Collections.singletonList(new ACL(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ | ZooDefs.Perms.WRITE, ANYONE_ID_UNSAFE));

    private static ACLProvider testACLProvider = new ACLProvider() {
        @Override
        public List<ACL> getDefaultAcl() {
            return ZooDefs.Ids.OPEN_ACL_UNSAFE;
        }

        @Override
        public List<ACL> getAclForPath(String path) {
            return switch (path) {
                case "/bar" -> READ_CREATE;
                case "/bar/foo" -> READ_CREATE_WRITE;
                default -> null;
            };
        }
    };

    private CuratorFramework createClient(ACLProvider aclProvider) {
        return CuratorFrameworkFactory.builder().
                aclProvider(aclProvider).
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build();
    }

    @Test
    public void testCreateWithParentsWithAcl() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            String path = "/bar/foo";
            List<ACL> acl = Collections.singletonList(new ACL(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ, ANYONE_ID_UNSAFE));
            client.create().creatingParentsIfNeeded().withACL(acl).forPath(path);
            List<ACL> actualBarFoo = client.getACL().forPath(path);
            assertEquals(actualBarFoo, acl);
            List<ACL> actualBar = client.getACL().forPath("/bar");
            assertEquals(actualBar, ZooDefs.Ids.OPEN_ACL_UNSAFE);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateWithParentsWithAclApplyToParents() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            String path = "/bar/foo";
            List<ACL> acl = Collections.singletonList(new ACL(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ, ANYONE_ID_UNSAFE));
            client.create().creatingParentsIfNeeded().withACL(acl, true).forPath(path);
            List<ACL> actualBarFoo = client.getACL().forPath(path);
            assertEquals(actualBarFoo, acl);
            List<ACL> actualBar = client.getACL().forPath("/bar");
            assertEquals(actualBar, acl);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateWithParentsWithAclInBackground() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            final CountDownLatch latch = new CountDownLatch(1);
            String path = "/bar/foo";
            List<ACL> acl = Collections.singletonList(new ACL(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ, ANYONE_ID_UNSAFE));
            BackgroundCallback callback = (client1, event) -> latch.countDown();
            client.create().creatingParentsIfNeeded().withACL(acl).inBackground(callback).forPath(path);
            assertTrue(latch.await(2000, TimeUnit.MILLISECONDS), "Callback not invoked");
            List<ACL> actualBarFoo = client.getACL().forPath(path);
            assertEquals(actualBarFoo, acl);
            List<ACL> actualBar = client.getACL().forPath("/bar");
            assertEquals(actualBar, ZooDefs.Ids.OPEN_ACL_UNSAFE);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateWithParentsWithAclApplyToParentsInBackground() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            final CountDownLatch latch = new CountDownLatch(1);
            String path = "/bar/foo";
            List<ACL> acl = Collections.singletonList(new ACL(ZooDefs.Perms.CREATE | ZooDefs.Perms.READ, ANYONE_ID_UNSAFE));
            BackgroundCallback callback = (client1, event) -> latch.countDown();
            client.create().creatingParentsIfNeeded().withACL(acl, true).inBackground(callback).forPath(path);
            assertTrue(latch.await(2000, TimeUnit.MILLISECONDS), "Callback not invoked");
            List<ACL> actualBarFoo = client.getACL().forPath(path);
            assertEquals(actualBarFoo, acl);
            List<ACL> actualBar = client.getACL().forPath("/bar");
            assertEquals(actualBar, acl);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateWithParentsWithoutAcl() throws Exception {
        CuratorFramework client = createClient(testACLProvider);
        try {
            client.start();
            String path = "/bar/foo/boo";
            client.create().creatingParentsIfNeeded().forPath(path);
            List<ACL> actualBarFooBoo = client.getACL().forPath("/bar/foo/boo");
            assertEquals(actualBarFooBoo, ZooDefs.Ids.OPEN_ACL_UNSAFE);
            List<ACL> actualBarFoo = client.getACL().forPath("/bar/foo");
            assertEquals(actualBarFoo, READ_CREATE_WRITE);
            List<ACL> actualBar = client.getACL().forPath("/bar");
            assertEquals(actualBar, READ_CREATE);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateWithParentsWithoutAclInBackground() throws Exception {
        CuratorFramework client = createClient(testACLProvider);
        try {
            client.start();
            final CountDownLatch latch = new CountDownLatch(1);
            BackgroundCallback callback = (client1, event) -> latch.countDown();
            final String path = "/bar/foo/boo";
            client.create().creatingParentsIfNeeded().inBackground(callback).forPath(path);
            assertTrue(latch.await(2000, TimeUnit.MILLISECONDS), "Callback not invoked");
            List<ACL> actualBarFooBoo = client.getACL().forPath(path);
            assertEquals(actualBarFooBoo, ZooDefs.Ids.OPEN_ACL_UNSAFE);
            List<ACL> actualBarFoo = client.getACL().forPath("/bar/foo");
            assertEquals(actualBarFoo, READ_CREATE_WRITE);
            List<ACL> actualBar = client.getACL().forPath("/bar");
            assertEquals(actualBar, READ_CREATE);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    private void check(CuratorFramework client, CreateBuilderMain builder, String path, byte[] data, boolean expectedSuccess) throws Exception {
        int expectedCode = (expectedSuccess) ? KeeperException.Code.OK.intValue() : KeeperException.Code.NODEEXISTS.intValue();
        try {
            builder.forPath(path, data);
            assertEquals(expectedCode, KeeperException.Code.OK.intValue());
            Stat stat = new Stat();
            byte[] actualData = client.getData().storingStatIn(stat).forPath(path);
            assertTrue(IdempotentUtils.matches(0, data, stat.getVersion(), actualData));
        } catch (KeeperException e) {
            assertEquals(expectedCode, e.getCode());
        }
    }

    private void checkBackground(CuratorFramework client, CreateBuilderMain builder, String path, byte[] data, boolean expectedSuccess) throws Exception {
        int expectedCode = (expectedSuccess) ? KeeperException.Code.OK.intValue() : KeeperException.Code.NODEEXISTS.intValue();
        AtomicInteger actualCode = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);
        BackgroundCallback callback = (client1, event) -> {
            actualCode.set(event.getResultCode());
            latch.countDown();
        };
        builder.inBackground(callback).forPath(path, data);
        assertTrue(latch.await(5000, TimeUnit.MILLISECONDS), "Callback not invoked");
        assertEquals(expectedCode, actualCode.get());
        if (expectedCode == KeeperException.Code.OK.intValue()) {
            Stat stat = new Stat();
            byte[] actualData = client.getData().storingStatIn(stat).forPath(path);
            assertTrue(IdempotentUtils.matches(0, data, stat.getVersion(), actualData));
        }
    }

    private CreateBuilderMain clBefore(CreateBuilderMain builder) {
        ((CreateBuilderImpl) builder).failBeforeNextCreateForTesting = true;
        return builder;
    }

    private CreateBuilderMain clAfter(CreateBuilderMain builder) {
        ((CreateBuilderImpl) builder).failNextCreateForTesting = true;
        return builder;
    }

    private CreateBuilderMain clCheck(CreateBuilderMain builder) {
        ((CreateBuilderImpl) builder).failNextIdempotentCheckForTesting = true;
        return builder;
    }

    @Test
    public void testBackgroundFaultInjectionHang() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            Stat stat = new Stat();
            String path = "/create";
            byte[] data = new byte[]{1, 2};
            CreateBuilderMain create = client.create();
            check(client, create, path, data, true);
            checkBackground(client, clAfter(client.create()), path, data, false);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testIdempotentCreate() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            Stat stat = new Stat();
            String path = "/idpcreate";
            String pathBack = "/idpcreateback";
            byte[] data1 = new byte[]{1, 2, 3};
            byte[] data2 = new byte[]{4, 5, 6};
            check(client, client.create().idempotent(), path, data1, true);
            checkBackground(client, client.create().idempotent(), pathBack, data1, true);
            check(client, client.create().idempotent(), path, data1, true);
            checkBackground(client, client.create().idempotent(), pathBack, data1, true);
            check(client, client.create().idempotent(), path, data2, false);
            checkBackground(client, client.create().idempotent(), pathBack, data2, false);
            client.setData().forPath(path, data2);
            client.setData().forPath(pathBack, data2);
            check(client, client.create().idempotent(), path, data1, false);
            checkBackground(client, client.create().idempotent(), pathBack, data1, false);
            check(client, client.create().idempotent(), path, data2, false);
            checkBackground(client, client.create().idempotent(), pathBack, data2, false);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testIdempotentCreateConnectionLoss() throws Exception {
        CuratorFramework client = createClient(new DefaultACLProvider());
        try {
            client.start();
            String path1 = "/idpcreate1";
            String path2 = "/idpcreate2";
            String path3 = "/create3";
            String path4 = "/create4";
            byte[] data = new byte[]{1, 2, 3};
            byte[] data2 = new byte[]{1, 2, 3, 4};
            check(client, clBefore(client.create().idempotent()), path1, data, true);
            checkBackground(client, clBefore(client.create().idempotent()), path1 + "back", data, true);
            check(client, clAfter(client.create().idempotent()), path2, data, true);
            checkBackground(client, clAfter(client.create().idempotent()), path2 + "back", data, true);
            check(client, clBefore(client.create().idempotent()), path1, data, true);
            checkBackground(client, clBefore(client.create().idempotent()), path1 + "back", data, true);
            check(client, clAfter(client.create().idempotent()), path2, data, true);
            checkBackground(client, clAfter(client.create().idempotent()), path2 + "back", data, true);
            check(client, clCheck(client.create().idempotent()), path2, data, true);
            checkBackground(client, clCheck(client.create().idempotent()), path2 + "back", data, true);
            check(client, clBefore(client.create().idempotent()), path1, data2, false);
            checkBackground(client, clBefore(client.create().idempotent()), path1 + "back", data2, false);
            check(client, clAfter(client.create().idempotent()), path2, data2, false);
            checkBackground(client, clAfter(client.create().idempotent()), path2 + "back", data2, false);
            check(client, clCheck(client.create().idempotent()), path2, data2, false);
            checkBackground(client, clCheck(client.create().idempotent()), path2 + "back", data2, false);
            check(client, clBefore(client.create()), path3, data, true);
            checkBackground(client, clBefore(client.create()), path3 + "back", data, true);
            check(client, clAfter(client.create()), path4, data, false);
            checkBackground(client, clAfter(client.create()), path4 + "back", data, false);
        } finally {
            CloseableUtils.closeQuietly(client);
        }
    }

    @Test
    public void testCreateProtectedUtils() throws Exception {
        try (CuratorFramework client = CuratorFrameworkFactory.builder().
                connectString(server.getConnectString()).
                retryPolicy(new RetryOneTime(1)).
                build()) {
            client.start();
            client.blockUntilConnected();
            client.create().forPath("/parent");
            assertEquals(client.getChildren().forPath("/parent").size(), 0);
            client.create().withProtection().withMode(CreateMode.EPHEMERAL).forPath("/parent/test");
            final List<String> children = client.getChildren().forPath("/parent");
            assertEquals(1, children.size());
            final String testZNodeName = children.get(0);
            assertEquals(testZNodeName.length(), ProtectedUtils.PROTECTED_PREFIX_WITH_UUID_LENGTH + "test".length());
            assertTrue(testZNodeName.startsWith(ProtectedUtils.PROTECTED_PREFIX));
            assertEquals(testZNodeName.charAt(ProtectedUtils.PROTECTED_PREFIX_WITH_UUID_LENGTH - 1), ProtectedUtils.PROTECTED_SEPARATOR);
            assertTrue(ProtectedUtils.isProtectedZNode(testZNodeName));
            assertEquals(ProtectedUtils.normalize(testZNodeName), "test");
            assertFalse(ProtectedUtils.isProtectedZNode("parent"));
            assertEquals(ProtectedUtils.normalize("parent"), "parent");
        }
    }

    @Test
    public void testProtectedUtils() {
        String name = "_c_53345f98-9423-4e0c-a7b5-9f819e3ec2e1-yo";
        assertTrue(ProtectedUtils.isProtectedZNode(name));
        assertEquals(ProtectedUtils.normalize(name), "yo");
        assertEquals(ProtectedUtils.extractProtectedId(name).get(), "53345f98-9423-4e0c-a7b5-9f819e3ec2e1");
        name = "c_53345f98-9423-4e0c-a7b5-9f819e3ec2e1-yo";
        assertFalse(ProtectedUtils.isProtectedZNode(name));
        assertEquals(ProtectedUtils.normalize(name), name);
        assertEquals(ProtectedUtils.extractProtectedId(name), Optional.<String>empty());
        name = "_c_53345f98-hola-4e0c-a7b5-9f819e3ec2e1-yo";
        assertFalse(ProtectedUtils.isProtectedZNode(name));
        assertEquals(ProtectedUtils.normalize(name), name);
        assertEquals(ProtectedUtils.extractProtectedId(name), Optional.<String>empty());
        name = "_c_53345f98-hola-4e0c-a7b5-9f819e3ec2e1+yo";
        assertFalse(ProtectedUtils.isProtectedZNode(name));
        assertEquals(ProtectedUtils.normalize(name), name);
        assertEquals(ProtectedUtils.extractProtectedId(name), Optional.<String>empty());
        name = "_c_53345f98-9423-4e0c-a7b5-9f819e3ec2e1-yo";
        assertEquals(name, ProtectedUtils.toProtectedZNode("yo", "53345f98-9423-4e0c-a7b5-9f819e3ec2e1"));
        assertEquals("yo", ProtectedUtils.toProtectedZNode("yo", null));
        String path = ZKPaths.makePath("hola", "yo");
        assertEquals(ProtectedUtils.toProtectedZNodePath(path, "53345f98-9423-4e0c-a7b5-9f819e3ec2e1"), ZKPaths.makePath("hola", name));
        assertEquals(ProtectedUtils.toProtectedZNodePath(path, null), path);
        path = ZKPaths.makePath("hola", name);
        assertEquals(ProtectedUtils.normalizePath(path), "/hola/yo");
    }
}
