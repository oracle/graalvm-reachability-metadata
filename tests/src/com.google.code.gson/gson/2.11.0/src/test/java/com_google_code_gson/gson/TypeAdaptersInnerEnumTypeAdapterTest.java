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

public class TypeAdaptersInnerEnumTypeAdapterTest {
    @Test
    void mapsAnnotatedEnumNamesAliasesAndStringRepresentation() {
        Gson gson = new Gson();

        assertThat(gson.fromJson("\"ready\"", Status.class)).isEqualTo(Status.READY);
        assertThat(gson.fromJson("\"queued\"", Status.class)).isEqualTo(Status.READY);
        assertThat(gson.fromJson("\"waiting for dispatch\"", Status.class)).isEqualTo(Status.READY);
        assertThat(gson.toJson(Status.READY)).isEqualTo("\"ready\"");
        assertThat(gson.fromJson("\"SENT\"", Status.class)).isEqualTo(Status.SENT);
        assertThat(gson.toJson(Status.SENT)).isEqualTo("\"SENT\"");
    }

    private enum Status {
        @SerializedName(value = "ready", alternate = {"queued", "pending"})
        READY,
        SENT;

        @Override
        public String toString() {
            return this == READY ? "waiting for dispatch" : "dispatched";
        }
    }
}
