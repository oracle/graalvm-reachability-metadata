/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

public class EnumTypeAdapterTest {
    @Test
    void serializesEnumUsingSerializedName() {
        Gson gson = new Gson();

        String json = gson.toJson(WorkflowState.STARTED);

        assertThat(json).isEqualTo("\"started\"");
    }

    @Test
    void deserializesEnumUsingSerializedNameAlternate() {
        Gson gson = new Gson();

        WorkflowState state = gson.fromJson("\"queued\"", WorkflowState.class);

        assertThat(state).isEqualTo(WorkflowState.STARTED);
    }

    @Test
    void deserializesEnumUsingToStringFallback() {
        Gson gson = new Gson();

        WorkflowState state = gson.fromJson("\"done\"", WorkflowState.class);

        assertThat(state).isEqualTo(WorkflowState.FINISHED);
    }

    private enum WorkflowState {
        @SerializedName(value = "started", alternate = {"queued", "running"})
        STARTED,

        FINISHED;

        @Override
        public String toString() {
            if (this == FINISHED) {
                return "done";
            }
            return name();
        }
    }
}
