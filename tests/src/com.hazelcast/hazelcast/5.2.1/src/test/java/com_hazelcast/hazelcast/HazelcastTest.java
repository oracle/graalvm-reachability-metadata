/*
 * Copyright and related rights waived via CC0
 *
 * You should have received the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.ringbuffer.Ringbuffer;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HazelcastTest {

    private HazelcastInstance hz;

    @BeforeEach
    void setUp() {
        // Isolate each test in its own single-member cluster with discovery disabled
        Config config = new Config();
        config.setClusterName("hz-test-" + UUID.randomUUID());

        JoinConfig join = config.getNetworkConfig().getJoin();
        // Disable all joining strategies to ensure a single-node cluster
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);

        hz = Hazelcast.newHazelcastInstance(config);
    }

    @AfterEach
    void tearDown() {
        if (hz != null) {
            hz.shutdown();
        }
    }

    @Test
    void map_basicOperations_andEntryListener() throws Exception {
        IMap<String, Integer> map = hz.getMap("numbers");
        CountDownLatch addLatch = new CountDownLatch(1);

        map.addEntryListener((EntryAddedListener<String, Integer>) event -> {
            if ("three".equals(event.getKey()) && Integer.valueOf(3).equals(event.getValue())) {
                addLatch.countDown();
            }
        }, true);

        // basic put/get/size
        map.put("one", 1);
        map.putIfAbsent("two", 2);

        assertThat(map.get("one")).isEqualTo(1);
        assertThat(map.get("two")).isEqualTo(2);
        assertThat(map.size()).isEqualTo(2);

        // listener should observe this addition
        map.put("three", 3);
        assertThat(addLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // replace and compute
        map.replace("two", 22);
        map.computeIfPresent("one", (k, v) -> v + 10);

        assertThat(map.get("two")).isEqualTo(22);
        assertThat(map.get("one")).isEqualTo(11);

        map.destroy();
    }

    @Test
    void topic_publishAndReceive() throws Exception {
        ITopic<String> topic = hz.getTopic("news");
        CountDownLatch received = new CountDownLatch(1);

        MessageListener<String> listener = new MessageListener<String>() {
            @Override
            public void onMessage(Message<String> message) {
                if ("hello hazelcast".equals(message.getMessageObject())) {
                    received.countDown();
                }
            }
        };
        topic.addMessageListener(listener);

        topic.publish("hello hazelcast");

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void executorService_runCallableOnMember() throws Exception {
        IExecutorService exec = hz.getExecutorService("exec");
        String payload = "work";
        String result = exec.submit(new EchoTask(payload)).get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("echo:" + payload);
        exec.shutdown();
    }

    @Test
    void ringbuffer_addAndRead() throws Exception {
        Ringbuffer<String> rb = hz.getRingbuffer("rb");
        long seqA = rb.add("A");
        long seqB = rb.add("B");

        assertThat(rb.readOne(seqA)).isEqualTo("A");
        assertThat(rb.readOne(seqB)).isEqualTo("B");
    }

    // Simple serializable task used with IExecutorService
    public static class EchoTask implements Callable<String>, Serializable {
        private final String input;

        public EchoTask(String input) {
            this.input = input;
        }

        @Override
        public String call() {
            return "echo:" + input;
        }
    }
}
