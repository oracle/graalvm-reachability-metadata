/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.util.IntrospectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilsTest {
    @Test
    void usesDynamicPropertyAccessorsAndInvokesConfiguredOperation() throws Exception {
        PropertiesBean bean = new PropertiesBean();
        assertThat(IntrospectionUtils.setProperty(bean, "color", "blue")).isTrue();
        assertThat(IntrospectionUtils.getProperty(bean, "color")).isEqualTo("blue");
        assertThat(IntrospectionUtils.callMethodN(bean, "describe", new Object[] {"color"},
                new Class<?>[] {String.class})).isEqualTo("color=blue");
        VoidPropertiesBean other = new VoidPropertiesBean();
        assertThat(IntrospectionUtils.setProperty(other, "size", "large")).isTrue();
        assertThat(other.values).containsEntry("size", "large");
    }

    public static class PropertiesBean {
        private final Map<String, String> values = new HashMap<>();

        public boolean setProperty(String name, String value) {
            values.put(name, value);
            return true;
        }

        public String getProperty(String name) {
            return values.get(name);
        }

        public String describe(String name) {
            return name + "=" + values.get(name);
        }
    }

    public static class VoidPropertiesBean {
        private final Map<String, String> values = new HashMap<>();

        public void setProperty(String name, String value) {
            values.put(name, value);
        }
    }
}
