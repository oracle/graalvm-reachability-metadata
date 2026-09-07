/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldUtilsTest {
    @Test
    void discoversReadsAndWritesFieldsAcrossAClassHierarchy() throws Exception {
        Fields target = new Fields();

        assertThat(FieldUtils.getField(Fields.class, "visible")).isNotNull();
        assertThat(FieldUtils.getField(Fields.class, "ENTRY")).isNotNull();
        assertThat(FieldUtils.getDeclaredField(Fields.class, "hidden", true)).isNotNull();
        assertThat(FieldUtils.getAllFieldsList(Fields.class))
            .extracting(Field::getName)
            .contains("visible", "hidden", "base");
        assertThat(FieldUtils.readField(target, "visible")).isEqualTo("initial");
        FieldUtils.writeField(target, "visible", "updated");
        assertThat(target.visible).isEqualTo("updated");
        assertThat(FieldUtils.readDeclaredField(target, "hidden", true)).isEqualTo(7);
    }

    public static class BaseFields {
        private final String base = "base";
    }

    public interface Constants {
        String ENTRY = "entry";
    }

    public static class Fields extends BaseFields implements Constants {
        public String visible = "initial";
        private final int hidden = 7;
    }
}
