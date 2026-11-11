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

public class TypeAdaptersInnerEnumTypeAdapterAnonymous1Test {
    @Test
    void discoversOnlyEnumConstantsIncludingConstantSpecificSubclass() {
        Gson gson = new Gson();

        Channel[] channels = gson.fromJson("""
                ["email","PUSH"]
                """, Channel[].class);

        assertThat(channels).containsExactly(Channel.EMAIL, Channel.PUSH);
        assertThat(channels[0].transport()).isEqualTo("smtp");
        assertThat(gson.toJson(Channel.EMAIL)).isEqualTo("\"email\"");
        assertThat(gson.toJson(channels)).isEqualTo("[\"email\",\"PUSH\"]");
    }

    private enum Channel {
        @SerializedName("email")
        EMAIL("mail") {
            @Override
            String transport() {
                return "smtp";
            }
        },
        PUSH("notification");

        private final String transport;

        Channel(String transport) {
            this.transport = transport;
        }

        String transport() {
            return transport;
        }
    }
}
