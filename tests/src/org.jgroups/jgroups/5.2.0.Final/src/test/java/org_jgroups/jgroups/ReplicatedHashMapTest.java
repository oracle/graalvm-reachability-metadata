/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jgroups.JChannel;
import org.jgroups.blocks.ReplicatedHashMap;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReplicatedHashMapTest {
    @Test
    void transfersMapContentsThroughStateStreams() throws Exception {
        ConcurrentHashMap<String, String> contents = new ConcurrentHashMap<>(Map.of("alpha", "one", "bravo", "two"));

        try (ReplicatedHashMap<String, String> source = new ReplicatedHashMap<>(contents,
                new JChannel(new SHARED_LOOPBACK()));
                ReplicatedHashMap<String, String> target = new ReplicatedHashMap<>(
                        new JChannel(new SHARED_LOOPBACK()))) {
            ByteArrayOutputStream state = new ByteArrayOutputStream();

            source.getState(state);
            target.setState(new ByteArrayInputStream(state.toByteArray()));

            assertThat(target).containsEntry("alpha", "one")
                    .containsEntry("bravo", "two")
                    .hasSize(2);
        }
    }
}
