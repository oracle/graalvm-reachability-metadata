/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeAdaptersInnerEnumTypeAdapterTest {
    public enum TaskState {
        @SerializedName("in-progress")
        IN_PROGRESS,
        DONE
    }

    @Test
    public void serializesEnumConstantsWithSerializedNames() {
        final Gson gson = new Gson();

        final String json = gson.toJson(TaskState.IN_PROGRESS);

        assertThat(json).isEqualTo("\"in-progress\"");
    }

    @Test
    public void deserializesEnumConstantsWithSerializedNames() {
        final Gson gson = new Gson();

        final TaskState annotatedState = gson.fromJson("\"in-progress\"", TaskState.class);
        final TaskState defaultState = gson.fromJson("\"DONE\"", TaskState.class);

        assertThat(annotatedState).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(defaultState).isEqualTo(TaskState.DONE);
    }
}
