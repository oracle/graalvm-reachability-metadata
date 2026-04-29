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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ReplicatedHashMapTest {
    @Test
    void serializesAndRestoresMapState() throws Exception {
        try (ReplicatedHashMap<String, String> replicatedMap = new ReplicatedHashMap<>(new JChannel(new SHARED_LOOPBACK()))) {
            replicatedMap._put("primary", "alpha");
            replicatedMap._put("secondary", "bravo");
            ByteArrayOutputStream state = new ByteArrayOutputStream();

            replicatedMap.getState(state);
            replicatedMap._clear();
            replicatedMap.setState(new ByteArrayInputStream(state.toByteArray()));

            assertThat(replicatedMap)
                .containsEntry("primary", "alpha")
                .containsEntry("secondary", "bravo")
                .hasSize(2);
        }
    }
}
