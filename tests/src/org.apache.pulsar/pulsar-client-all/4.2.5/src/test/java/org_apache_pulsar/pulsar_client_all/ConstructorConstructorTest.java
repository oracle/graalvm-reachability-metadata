/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.apache.pulsar.shade.com.google.gson.GsonBuilder;
import org.apache.pulsar.shade.com.google.gson.JsonIOException;
import org.junit.jupiter.api.Test;

public class ConstructorConstructorTest {
    @Test
    void invokesNoArgumentConstructorDuringDeserialization() {
        ConstructedValue value = new Gson().fromJson("{\"name\":\"pulsar\"}", ConstructedValue.class);

        assertThat(value.name).isEqualTo("pulsar");
        assertThat(value.constructorCalled).isTrue();
    }

    @Test
    void reportsMissingNoArgumentConstructorWhenUnsafeIsDisabled() {
        Gson gson = new GsonBuilder().disableJdkUnsafe().create();

        assertThatThrownBy(() -> gson.fromJson("{\"name\":\"pulsar\"}", ConstructorOnlyValue.class))
                .isInstanceOf(JsonIOException.class)
                .hasMessageContaining("Unable to create instance")
                .hasMessageContaining("JDK Unsafe is disabled");
    }

    public static class ConstructedValue {
        private String name;
        private boolean constructorCalled;

        public ConstructedValue() {
            constructorCalled = true;
        }
    }

    public static class ConstructorOnlyValue {
        private String name;

        public ConstructorOnlyValue(String name) {
            this.name = name;
        }
    }
}
