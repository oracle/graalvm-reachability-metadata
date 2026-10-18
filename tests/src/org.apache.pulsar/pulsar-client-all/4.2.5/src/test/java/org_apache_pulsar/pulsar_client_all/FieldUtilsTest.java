/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

public class FieldUtilsTest {

    @Test
    void discoversReadsAndWritesInheritedAndDeclaredFields() throws Exception {
        final FieldTarget target = new FieldTarget();
        final Field name = FieldUtils.getDeclaredField(FieldTarget.class, "name", true);

        assertThat(FieldUtils.getAllFieldsList(FieldTarget.class))
                .extracting(Field::getName)
                .contains("name", "identifier");
        assertThat(FieldUtils.readField(name, target, true)).isEqualTo("initial");

        FieldUtils.writeField(name, target, "updated", true);
        FieldUtils.writeField(target, "identifier", 42L, true);

        assertThat(FieldUtils.readDeclaredField(target, "name", true)).isEqualTo("updated");
        assertThat(FieldUtils.readField(target, "identifier", true)).isEqualTo(42L);
    }

    public static class FieldBase {
        protected long identifier = 1L;
    }

    public static final class FieldTarget extends FieldBase {
        private String name = "initial";
    }
}
