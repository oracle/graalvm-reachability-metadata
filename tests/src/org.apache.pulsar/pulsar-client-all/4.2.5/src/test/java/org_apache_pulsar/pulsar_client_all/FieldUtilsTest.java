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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class FieldUtilsTest {
    @Test
    void discoversReadsAndWritesInheritedAndDeclaredFields() throws Exception {
        ChildFields target = new ChildFields();

        Field hidden = FieldUtils.getDeclaredField(ChildFields.class, "hidden", true);
        Field inherited = FieldUtils.getField(ChildFields.class, "inherited");
        Field interfaceField = FieldUtils.getField(ChildFields.class, "CATEGORY");
        FieldUtils.writeField(target, "hidden", "updated", true);

        assertThat(FieldUtils.getAllFieldsList(ChildFields.class))
                .extracting(Field::getName)
                .contains("hidden", "inherited");
        assertThat(hidden).isNotNull();
        assertThat(inherited).isNotNull();
        assertThat(interfaceField).isNotNull();
        assertThat(FieldUtils.readField(target, "hidden", true)).isEqualTo("updated");
        assertThat(FieldUtils.readField(inherited, target)).isEqualTo("parent");
        assertThat(FieldUtils.readStaticField(interfaceField)).isEqualTo("pulsar");
    }

    public interface Categorized {
        String CATEGORY = "pulsar";
    }

    public static class ParentFields {
        public String inherited = "parent";
    }

    public static final class ChildFields extends ParentFields implements Categorized {
        private String hidden = "initial";
    }
}
