/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {
    @Test
    void createsInstancesAndInspectsMembers() {
        final Inspected value = ClassUtil.createInstance(Inspected.class, true);

        assertThat(value.label).isEqualTo("created");
        assertThat(ClassUtil.findConstructor(Inspected.class, true)).isNotNull();
        assertThat(ClassUtil.getConstructors(Inspected.class)).isNotEmpty();
        assertThat(ClassUtil.getDeclaredFields(Inspected.class))
                .extracting(field -> field.getName())
                .contains("label");
        assertThat(ClassUtil.getDeclaredMethods(Inspected.class))
                .extracting(method -> method.getName())
                .contains("getLabel");
        assertThat(ClassUtil.getClassMethods(Inspected.class))
                .extracting(method -> method.getName())
                .contains("getLabel");
    }

    public static final class Inspected {
        private final String label = "created";

        public String getLabel() {
            return label;
        }
    }
}
