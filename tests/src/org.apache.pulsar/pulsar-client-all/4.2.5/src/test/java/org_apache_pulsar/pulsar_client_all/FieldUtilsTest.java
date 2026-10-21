/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.Test;

public class FieldUtilsTest {
    @Test
    void discoversReadsAndWritesFieldsAcrossAClassHierarchy() throws Exception {
        MutableChild target = new MutableChild();
        Field hidden = FieldUtils.getDeclaredField(MutableChild.class, "message", true);
        Field inherited = FieldUtils.getField(MutableChild.class, "inherited", true);
        Field shared = FieldUtils.getField(MutableChild.class, "SHARED");
        List<Field> fields = FieldUtils.getAllFieldsList(MutableChild.class);

        assertThat(FieldUtils.readField(hidden, target)).isEqualTo("initial");
        FieldUtils.writeField(hidden, target, "updated");

        assertThat(target.message()).isEqualTo("updated");
        assertThat(inherited.getDeclaringClass()).isEqualTo(MutableParent.class);
        assertThat(shared.getDeclaringClass()).isEqualTo(SharedLabel.class);
        assertThat(fields).extracting(Field::getName).contains("message", "inherited");
    }

    public interface SharedLabel {
        String SHARED = "label";
    }

    public static class MutableParent {
        private String inherited = "parent";
    }

    public static class MutableChild extends MutableParent implements SharedLabel {
        private String message = "initial";

        public String message() {
            return message;
        }
    }
}
