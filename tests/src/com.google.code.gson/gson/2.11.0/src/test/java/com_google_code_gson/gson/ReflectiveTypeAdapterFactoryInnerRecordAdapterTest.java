/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryInnerRecordAdapterTest {
    @Test
    void appliesCompactCanonicalConstructorToDeserializedComponents() {
        Gson gson = new Gson();

        Destination destination = gson.fromJson("""
                {"code":"  eu-west  ","capacity":24}
                """, Destination.class);

        assertThat(destination.code()).isEqualTo("EU-WEST");
        assertThat(destination.capacity()).isEqualTo(24);
    }

    @Test
    void suppliesPrimitiveDefaultsForMissingRecordComponents() {
        Gson gson = new Gson();

        Subscription subscription = gson.fromJson("""
                {"topic":"events"}
                """, Subscription.class);

        assertThat(subscription.topic()).isEqualTo("events");
        assertThat(subscription.limit()).isZero();
        assertThat(subscription.active()).isFalse();
    }

    public record Destination(String code, int capacity) {
        public Destination {
            code = code.trim().toUpperCase(Locale.ROOT);
        }
    }

    public record Subscription(String topic, int limit, boolean active) {
    }
}
