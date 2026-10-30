/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_sgroschupf.zkclient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkBadVersionException;
import org.I0Itec.zkclient.exception.ZkNoNodeException;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.I0Itec.zkclient.serialize.BytesPushThroughSerializer;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ZkclientTest extends ZooKeeperTestSupport {
    @Test
    void persistsUpdatesAndRecursivelyDeletesNodeTree() {
        client.createPersistent("/settings/workers", true);
        client.createPersistent("/settings/workers", true);
        client.createPersistent("/settings/workers/primary", "ready");

        assertTrue(client.exists("/settings/workers/primary"));
        assertEquals(List.of("primary"), client.getChildren("/settings/workers"));
        assertEquals(1, client.countChildren("/settings/workers"));
        assertTrue(client.getCreationTime("/settings/workers/primary") > 0);

        Stat original = new Stat();
        assertEquals("ready", client.readData("/settings/workers/primary", original));
        client.<String>updateDataSerialized("/settings/workers/primary", value -> value + "-updated");

        Stat updated = new Stat();
        assertEquals("ready-updated", client.readData("/settings/workers/primary", updated));
        assertEquals(original.getVersion() + 1, updated.getVersion());
        assertThrows(ZkBadVersionException.class,
                () -> client.writeData("/settings/workers/primary", "stale", original.getVersion()));
        assertEquals("ready-updated", client.readData("/settings/workers/primary"));
        assertThrows(ZkNodeExistsException.class,
                () -> client.createPersistent("/settings/workers/primary", "duplicate"));

        assertTrue(client.deleteRecursive("/settings"));
        assertFalse(client.exists("/settings"));
        assertFalse(client.exists("/settings/workers/primary"));
    }

    @Test
    void supportsOptionalReadsAndDeletesOfAbsentNodes() {
        assertFalse(client.exists("/absent"));
        assertNull(client.readData("/absent", true));
        assertEquals(0, client.countChildren("/absent"));
        assertFalse(client.delete("/absent"));
        assertThrows(ZkNoNodeException.class, () -> client.readData("/absent"));
    }

    @Test
    void removesEphemeralNodesWhenTheirSessionCloses() {
        client.createPersistent("/sessions");
        ZkClient owner = new ZkClient(servers, 30000, 30000);
        String sequential;
        try {
            owner.createEphemeral("/sessions/owner", "active");
            sequential = owner.createEphemeralSequential("/sessions/worker-", "running");
            assertTrue(sequential.startsWith("/sessions/worker-"));
            assertEquals("active", client.readData("/sessions/owner"));
            assertEquals("running", client.readData(sequential));
            assertEquals(2, client.countChildren("/sessions"));
        } finally {
            owner.close();
        }
        assertFalse(client.exists("/sessions/owner"));
        assertFalse(client.exists(sequential));
        assertEquals(0, client.countChildren("/sessions"));
    }

    @Test
    void dataListenerReceivesRepeatedChangesAndDeletion() throws Exception {
        client.createPersistent("/status", "initial");
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        IZkDataListener listener = new IZkDataListener() {
            @Override
            public void handleDataChange(String path, Object data) {
                events.add(path + "=" + data);
            }

            @Override
            public void handleDataDeleted(String path) {
                events.add(path + "=deleted");
            }
        };
        client.subscribeDataChanges("/status", listener);
        try {
            assertEquals(1, client.numberOfListeners());
            client.writeData("/status", "starting");
            assertEquals("/status=starting", events.poll(20, TimeUnit.SECONDS));
            client.writeData("/status", "ready");
            assertEquals("/status=ready", events.poll(20, TimeUnit.SECONDS));
            assertTrue(client.delete("/status"));
            assertEquals("/status=deleted", events.poll(20, TimeUnit.SECONDS));
        } finally {
            client.unsubscribeDataChanges("/status", listener);
        }
        assertEquals(0, client.numberOfListeners());
    }

    @Test
    void childListenerReceivesCreationAndRemoval() throws Exception {
        client.createPersistent("/workers");
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        IZkChildListener listener = (path, children) -> events.add(path + "=" + children);
        assertEquals(List.of(), client.subscribeChildChanges("/workers", listener));
        try {
            assertEquals(1, client.numberOfListeners());
            client.createPersistent("/workers/primary", "ready");
            assertEquals("/workers=[primary]", events.poll(20, TimeUnit.SECONDS));
            assertTrue(client.delete("/workers/primary"));
            assertEquals("/workers=[]", events.poll(20, TimeUnit.SECONDS));
        } finally {
            client.unsubscribeChildChanges("/workers", listener);
        }
        assertEquals(0, client.numberOfListeners());
    }

    @Test
    void configurableSerializerStoresRawBytes() {
        client.setZkSerializer(new BytesPushThroughSerializer());
        byte[] initial = "starting".getBytes(StandardCharsets.UTF_8);
        byte[] updated = "ready".getBytes(StandardCharsets.UTF_8);
        client.createPersistent("/binary", initial);
        assertArrayEquals(initial, client.<byte[]>readData("/binary"));
        client.writeData("/binary", updated);
        assertArrayEquals(updated, client.<byte[]>readData("/binary"));
    }
}
