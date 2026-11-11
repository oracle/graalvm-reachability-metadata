/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import org.junit.jupiter.api.Test;

public class ReflectionAccessFilterHelperInnerAccessCheckerAnonymous1Test {
    @Test
    void permitsPublicConstructorAndFieldsWithoutOverridingAccess() {
        Gson gson = new GsonBuilder()
                .addReflectionAccessFilter(rawType -> FilterResult.BLOCK_INACCESSIBLE)
                .create();
        String json = """
                {"host":"registry","port":8848}
                """;

        Endpoint endpoint = gson.fromJson(json, Endpoint.class);

        assertThat(endpoint.host).isEqualTo("registry");
        assertThat(endpoint.port).isEqualTo(8848);
        assertThat(JsonParser.parseString(gson.toJson(endpoint))).isEqualTo(JsonParser.parseString(json));
    }

    @Test
    void refusesPrivateFieldsWhenAccessOverridesAreBlocked() {
        Gson gson = new GsonBuilder()
                .addReflectionAccessFilter(rawType -> FilterResult.BLOCK_INACCESSIBLE)
                .create();

        assertThatThrownBy(() -> gson.fromJson("""
                {"secret":"token"}
                """, PrivateEndpoint.class))
                .isInstanceOf(JsonIOException.class)
                .hasMessageContaining("is not accessible");
    }

    public static final class Endpoint {
        public String host;
        public int port;

        public Endpoint() {
        }
    }

    public static final class PrivateEndpoint {
        private String secret;

        public PrivateEndpoint() {
        }
    }
}
