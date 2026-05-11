/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.asm.BeansAccess;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;

public class JsonWriterAnonymous8Test {
    @Test
    void serializesAccessibleBeanFields() {
        String json = JSONValue.toJSONString(new DerivedBean());

        JSONObject parsed = (JSONObject) JSONValue.parse(json);
        assertThat(parsed)
                .containsEntry("publicText", "visible")
                .containsEntry("enabled", true)
                .containsEntry("count", 3)
                .containsEntry("baseNumber", 7);
    }

    public static class BaseBean {
        public int baseNumber = 7;
    }

    public static class DerivedBean extends BaseBean {
        public String publicText = "visible";
        public boolean enabled = true;
        public int count = 3;
    }

    public static class DerivedBeanAccAccess extends BeansAccess<DerivedBean> {
        @Override
        public void set(DerivedBean object, int methodIndex, Object value) {
            switch (getAccessors()[methodIndex].getName()) {
                case "baseNumber" -> object.baseNumber = ((Integer) value).intValue();
                case "count" -> object.count = ((Integer) value).intValue();
                case "enabled" -> object.enabled = ((Boolean) value).booleanValue();
                case "publicText" -> object.publicText = (String) value;
                default -> throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
            }
        }

        @Override
        public Object get(DerivedBean object, int methodIndex) {
            return switch (getAccessors()[methodIndex].getName()) {
                case "baseNumber" -> Integer.valueOf(object.baseNumber);
                case "count" -> Integer.valueOf(object.count);
                case "enabled" -> Boolean.valueOf(object.enabled);
                case "publicText" -> object.publicText;
                default -> throw new IllegalArgumentException("Unknown accessor index: " + methodIndex);
            };
        }

        @Override
        public DerivedBean newInstance() {
            return new DerivedBean();
        }
    }
}
