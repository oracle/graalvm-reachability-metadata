/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import net.minidev.json.reader.BeansWriter;
import org.junit.jupiter.api.Test;

public class BeansWriterTest {
    @Test
    void writesPublicFieldsAndBeanGetterProperties() throws Exception {
        StringBuilder out = new StringBuilder();

        new BeansWriter().writeJSONString(new MixedAccessBean(), out, JSONStyle.NO_COMPRESS);

        JSONObject parsed = (JSONObject) JSONValue.parse(out.toString());
        assertThat(parsed)
                .containsEntry("publicText", "visible")
                .containsEntry("count", 3)
                .containsEntry("active", true);
    }

    public static class MixedAccessBean {
        public String publicText = "visible";
        private int count = 3;
        private boolean active = true;

        public int getCount() {
            return count;
        }

        public boolean isActive() {
            return active;
        }
    }
}
