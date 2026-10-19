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

public class ReflectiveTypeAdapterFactoryTest {
    @Test
    void discoversInheritedFieldsForReflectiveMapping() {
        Gson gson = new Gson();
        ChildValue input = new ChildValue("parent", "child");

        String json = gson.toJson(input);
        ChildValue restored = gson.fromJson("{\"parent\":\"base\",\"child\":\"leaf\"}", ChildValue.class);

        assertThat(json).contains("\"parent\":\"parent\"").contains("\"child\":\"child\"");
        assertThat(restored.parent).isEqualTo("base");
        assertThat(restored.child).isEqualTo("leaf");
    }

    public static class ParentValue {
        private String parent;

        public ParentValue() {
        }

        public ParentValue(String parent) {
            this.parent = parent;
        }
    }

    public static class ChildValue extends ParentValue {
        private String child;

        public ChildValue() {
        }

        public ChildValue(String parent, String child) {
            super(parent);
            this.child = child;
        }
    }
}
