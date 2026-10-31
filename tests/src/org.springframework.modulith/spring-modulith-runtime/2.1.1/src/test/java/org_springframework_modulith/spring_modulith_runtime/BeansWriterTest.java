/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.minidev.json.JSONObject;
import org.springframework.modulith.runtime.minidev.json.JSONStyle;
import org.springframework.modulith.runtime.minidev.json.JSONValue;
import org.springframework.modulith.runtime.minidev.json.reader.BeansWriter;

public class BeansWriterTest {
    @Test
    void writesFieldsGettersAndInheritedProperties() throws Exception {
        StringBuilder output = new StringBuilder();

        new BeansWriter().writeJSONString(new ModuleDescription(), output, JSONStyle.NO_COMPRESS);

        JSONObject result = (JSONObject) JSONValue.parseWithException(output.toString());
        assertThat(result).hasSize(4)
                .containsEntry("name", "inventory")
                .containsEntry("displayName", "Inventory management")
                .containsEntry("active", true)
                .containsEntry("owner", "operations");
    }

    public static class OwnedModule {
        private String owner = "operations";

        public String getOwner() {
            return owner;
        }
    }

    public static class ModuleDescription extends OwnedModule {
        public String name = "inventory";
        private String displayName = "Inventory management";
        private boolean active = true;
        public transient String secret = "not exported";

        public String getDisplayName() {
            return displayName;
        }

        public boolean isActive() {
            return active;
        }
    }
}
