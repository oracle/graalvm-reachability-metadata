/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ConstructorConstructorTest {
    @Test
    void constructsObjectsUsingTheirDefaultConstructor() {
        final Constructed value = new Gson().fromJson("{\"name\":\"events\"}", Constructed.class);

        assertThat(value.constructorCalled).isTrue();
        assertThat(value.name).isEqualTo("events");
    }

    public static final class Constructed {
        private boolean constructorCalled;
        private String name;

        public Constructed() {
            constructorCalled = true;
        }
    }
}
