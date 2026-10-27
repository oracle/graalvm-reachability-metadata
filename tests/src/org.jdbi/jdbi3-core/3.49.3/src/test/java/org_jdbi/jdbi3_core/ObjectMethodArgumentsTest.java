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

public class ObjectMethodArgumentsTest {
    @Test
    void bindsValuesReturnedByPublicMethods() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:method_arguments;DB_CLOSE_DELAY=-1");
        MethodValues values = new MethodValues(111, "method-bound");

        String result = jdbi.withHandle(handle -> handle
                .createQuery("select cast(:id as varchar) || ':' || :label")
                .bindMethods(values)
                .mapTo(String.class)
                .one());

        assertThat(result).isEqualTo("111:method-bound");
    }

    public static class MethodValues {
        private final int id;
        private final String label;

        public MethodValues(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public int id() {
            return id;
        }

        public String label() {
            return label;
        }
    }
}
