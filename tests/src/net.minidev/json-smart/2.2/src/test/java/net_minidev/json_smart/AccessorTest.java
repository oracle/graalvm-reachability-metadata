/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.json.JSONValue;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class AccessorTest {
    @Test
    void serializesBeanPropertyWithStandardGetter() {
        try {
            String json = JSONValue.toJSONString(new StandardGetterBean());

            assertThat(json).isEqualTo("{\"name\":\"json-smart\"}");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void serializesBooleanBeanPropertyWithGetterFallback() {
        try {
            String json = JSONValue.toJSONString(new BooleanGetterOnlyBean());

            assertThat(json).isEqualTo("{\"enabled\":true}");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class StandardGetterBean {
        private String name = "json-smart";

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class BooleanGetterOnlyBean {
        private boolean enabled = true;

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean getEnabled() {
            return enabled;
        }
    }
}
