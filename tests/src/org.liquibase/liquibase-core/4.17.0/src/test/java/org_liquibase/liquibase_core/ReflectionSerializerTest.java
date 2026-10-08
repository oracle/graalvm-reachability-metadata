/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.serializer.ReflectionSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionSerializerTest {

    @Test
    void setsPrivateFieldValue() {
        MutableSerializableObject object = new MutableSerializableObject();

        ReflectionSerializer.getInstance().setValue(object, "value", "updated");

        assertThat(object.getValue()).isEqualTo("updated");
    }

    private static final class MutableSerializableObject {
        private String value = "initial";

        private String getValue() {
            return value;
        }
    }
}
