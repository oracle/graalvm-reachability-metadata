/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectFieldArgumentsTest {
    @Test
    void bindsAllPublicFields() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:field_arguments;DB_CLOSE_DELAY=-1");
        FieldValues values = new FieldValues(101, "field-bound");

        String result = jdbi.withHandle(handle -> handle
                .createQuery("select cast(:id as varchar) || ':' || :label")
                .bindFields(values)
                .mapTo(String.class)
                .one());

        assertThat(result).isEqualTo("101:field-bound");
    }

    public static class FieldValues {
        public int id;
        public String label;

        public FieldValues(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
