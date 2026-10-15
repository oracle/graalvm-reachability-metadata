/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class FieldPropertyTest {
    @Test
    void updatesPublicFieldsFromJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PartitionState state = new PartitionState();
        state.owner = "before";
        state.backlog = 1;

        PartitionState updated = mapper.readerForUpdating(state)
                .readValue("{\"owner\":\"broker-a\",\"backlog\":12}");

        assertThat(updated).isSameAs(state);
        assertThat(updated.owner).isEqualTo("broker-a");
        assertThat(updated.backlog).isEqualTo(12);
    }

    public static final class PartitionState {
        public String owner;
        public int backlog;
    }
}
