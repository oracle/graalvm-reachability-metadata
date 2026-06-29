/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.JChannel;
import org.jgroups.blocks.ReplicatedHashMap;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ReplicatedHashMapTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void serializesAndAppliesReplicatedMapState() throws Exception {
        ConcurrentMap<String, String> sourceEntries = new ConcurrentHashMap<>();
        sourceEntries.put("alpha", "one");
        sourceEntries.put("bravo", "two");
        ReplicatedHashMap<String, String> source = new ReplicatedHashMap<>(sourceEntries, loopbackChannel());
        ReplicatedHashMap<String, String> target = new ReplicatedHashMap<>(loopbackChannel());
        try {
            ByteArrayOutputStream serializedState = new ByteArrayOutputStream();

            source.getState(serializedState);
            target.setState(new ByteArrayInputStream(serializedState.toByteArray()));

            assertThat(target)
                    .containsEntry("alpha", "one")
                    .containsEntry("bravo", "two")
                    .hasSize(2);
        }
        finally {
            target.stop();
            source.stop();
        }
    }

    private static JChannel loopbackChannel() throws Exception {
        return new JChannel(new SHARED_LOOPBACK());
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }
}
