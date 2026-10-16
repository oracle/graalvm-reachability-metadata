/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

public class FieldUtilsTest {
    @Test
    void discoversReadsAndWritesDeclaredAndInheritedFields() throws Exception {
        final Child fixture = new Child();

        assertThat(FieldUtils.getAllFieldsList(Child.class))
                .extracting(field -> field.getName())
                .contains("privateValue", "inheritedValue");
        assertThat(FieldUtils.getDeclaredField(Child.class, "privateValue", true)).isNotNull();
        assertThat(FieldUtils.getField(Child.class, "inheritedValue")).isNotNull();
        assertThat(FieldUtils.readField(fixture, "inheritedValue")).isEqualTo("base");

        FieldUtils.writeDeclaredField(fixture, "privateValue", "updated", true);
        assertThat(FieldUtils.readDeclaredField(fixture, "privateValue", true)).isEqualTo("updated");
    }

    public static class Parent {
        public String inheritedValue = "base";
    }

    public static final class Child extends Parent {
        private String privateValue = "initial";
    }
}
