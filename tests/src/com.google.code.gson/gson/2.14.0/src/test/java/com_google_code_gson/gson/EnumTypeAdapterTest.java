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
    void serializesAndDeserializesEnumConstants() {
        Gson gson = new Gson();

        Priority actual = gson.fromJson("\"critical\"", Priority.class);
        String json = gson.toJson(Priority.MEDIUM);

        assertThat(actual).isEqualTo(Priority.HIGH);
        assertThat(json).isEqualTo("\"medium-priority\"");
    }

    @Test
    void deserializesEnumConstantUsingAlternateSerializedName() {
        Gson gson = new Gson();

        Priority actual = gson.fromJson("\"urgent\"", Priority.class);

        assertThat(actual).isEqualTo(Priority.HIGH);
    }

    private enum Priority {
        LOW,

        @SerializedName(value = "medium-priority")
        MEDIUM,

        @SerializedName(value = "critical", alternate = {"urgent", "p1"})
        HIGH
    }
}
