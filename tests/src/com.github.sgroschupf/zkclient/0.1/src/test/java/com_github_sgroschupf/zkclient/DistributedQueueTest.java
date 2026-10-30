/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_sgroschupf.zkclient;

import org.I0Itec.zkclient.DistributedQueue;
import org.I0Itec.zkclient.ZkClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class DistributedQueueTest extends ZooKeeperTestSupport {
    @Test
    void clientsSharePersistentQueueInInsertionOrder() {
        client.createPersistent("/jobs");
        DistributedQueue<String> producer = new DistributedQueue<>(client, "/jobs");
        assertTrue(producer.isEmpty());
        assertNull(producer.peek());
        assertNull(producer.poll());
        assertTrue(producer.offer("index"));
        assertTrue(producer.offer("export"));
        assertTrue(producer.offer("notify"));
        assertFalse(producer.isEmpty());
        assertEquals("index", producer.peek());
        assertEquals("index", producer.peek());
        assertEquals(3, client.countChildren("/jobs"));

        ZkClient consumerClient = new ZkClient(servers, 30000, 30000);
        try {
            DistributedQueue<String> consumer = new DistributedQueue<>(consumerClient, "/jobs");
            assertEquals("index", consumer.poll());
            assertEquals("export", consumer.peek());
            assertEquals("export", consumer.poll());
            assertEquals("notify", consumer.poll());
            assertTrue(consumer.isEmpty());
            assertNull(consumer.poll());
        } finally {
            consumerClient.close();
        }
        assertTrue(producer.isEmpty());
        assertNull(producer.peek());
        assertEquals(0, client.countChildren("/jobs"));
    }
}
