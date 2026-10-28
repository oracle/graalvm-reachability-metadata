/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.apache.pulsar.shade.com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

public class EnumTypeAdapterTest {
    @Test
    void mapsSerializedNamesAlternatesAndToStringFallbacks() {
        Gson gson = new Gson();

        assertThat(gson.toJson(WorkflowState.STARTED)).isEqualTo("\"started\"");
        assertThat(gson.fromJson("\"queued\"", WorkflowState.class)).isEqualTo(WorkflowState.STARTED);
        assertThat(gson.fromJson("\"done\"", WorkflowState.class)).isEqualTo(WorkflowState.FINISHED);
    }

    public enum WorkflowState {
        @SerializedName(value = "started", alternate = {"queued", "running"})
        STARTED,
        FINISHED;

        @Override
        public String toString() {
            return this == FINISHED ? "done" : name();
        }
    }
}
