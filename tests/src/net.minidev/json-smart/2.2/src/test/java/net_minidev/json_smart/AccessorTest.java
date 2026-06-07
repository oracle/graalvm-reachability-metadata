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
