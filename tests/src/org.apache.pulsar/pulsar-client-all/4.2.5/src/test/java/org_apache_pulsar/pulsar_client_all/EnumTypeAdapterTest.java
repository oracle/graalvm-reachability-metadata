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
    void readsPrimaryAndAlternateSerializedEnumNames() {
        final Gson gson = new Gson();

        assertThat(gson.fromJson("\"ready\"", State.class)).isEqualTo(State.READY);
        assertThat(gson.fromJson("\"up\"", State.class)).isEqualTo(State.READY);
        assertThat(gson.toJson(State.READY)).isEqualTo("\"ready\"");
    }

    private enum State {
        @SerializedName(value = "ready", alternate = "up")
        READY
    }
}
