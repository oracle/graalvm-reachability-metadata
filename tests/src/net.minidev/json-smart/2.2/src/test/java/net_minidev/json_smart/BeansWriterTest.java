/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import net.minidev.json.reader.BeansWriter;
import org.junit.jupiter.api.Test;

public class BeansWriterTest {
    @Test
    void serializesPublicFieldsAndGetterBackedProperties() throws IOException {
        StringBuilder out = new StringBuilder();

        new BeansWriter().writeJSONString(new BeanWithMixedAccessors(), out, JSONStyle.NO_COMPRESS);

        JSONObject parsed = (JSONObject) JSONValue.parse(out.toString());
        assertThat(parsed)
                .containsEntry("publicName", "json-smart")
                .containsEntry("count", 42)
                .containsEntry("active", true);
    }

    public static class BeanWithMixedAccessors {
        public String publicName = "json-smart";
        private int count = 42;
        private boolean active = true;

        public int getCount() {
            return count;
        }

        public boolean isActive() {
            return active;
        }
    }
}
