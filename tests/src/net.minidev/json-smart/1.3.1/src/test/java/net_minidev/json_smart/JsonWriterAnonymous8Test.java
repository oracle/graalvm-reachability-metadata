/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;

public class JsonWriterAnonymous8Test {
    @Test
    void serializesBeanFieldsThroughPublicFieldAndGetterMethods() {
        String json = JSONValue.toJSONString(new DerivedBean());

        JSONObject parsed = (JSONObject) JSONValue.parse(json);
        assertThat(parsed)
                .containsEntry("publicText", "visible")
                .containsEntry("privateText", "from-getter")
                .containsEntry("enabled", true)
                .containsEntry("baseNumber", 7);
        assertThat(parsed).doesNotContainKey("ignoredWithoutGetter");
    }

    public static class BaseBean {
        public int baseNumber = 7;
    }

    public static class DerivedBean extends BaseBean {
        public String publicText = "visible";
        private String privateText = "from-getter";
        private boolean enabled = true;
        private String ignoredWithoutGetter = "hidden";

        public String getPrivateText() {
            return privateText;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
