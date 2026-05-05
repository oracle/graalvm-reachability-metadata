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
    private final Gson gson = new Gson();

    @Test
    void resolvesEnumConstantsThroughReflectedPublicFields() {
        ShipmentState state = gson.fromJson("\"shipped\"", ShipmentState.class);

        assertThat(state).isEqualTo(ShipmentState.IN_TRANSIT);
        assertThat(gson.toJson(ShipmentState.READY)).isEqualTo("\"ready\"");
    }

    public enum ShipmentState {
        @SerializedName("ready")
        READY,
        @SerializedName("shipped")
        IN_TRANSIT
    }
}
