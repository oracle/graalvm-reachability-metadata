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
import net.minidev.json.reader.JsonWriter;
import org.junit.jupiter.api.Test;

public class JsonWriterAnonymous9Test {
    @Test
    void serializesBeanWithLegacyReflectionWriter() throws IOException {
        StringBuilder out = new StringBuilder();

        JsonWriter.beansWriter.writeJSONString(new LegacyBean(), out, JSONStyle.NO_COMPRESS);

        JSONObject parsed = (JSONObject) JSONValue.parse(out.toString());
        assertThat(parsed)
                .containsEntry("publicNumber", 42)
                .containsEntry("name", "legacy")
                .containsEntry("enabled", true);
    }

    public static class LegacyBean {
        public int publicNumber = 42;
        private String name = "legacy";
        private boolean enabled = true;

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
