/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.SocketProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilsTest {
    @Test
    void configuresContainerPropertiesAndInvokesOperations() throws Exception {
        StandardContext context = new StandardContext();
        assertThat(IntrospectionUtils.setProperty(context, "path", "/catalog")).isTrue();
        assertThat(IntrospectionUtils.setProperty(context, "reloadable", "true")).isTrue();
        assertThat(IntrospectionUtils.getProperty(context, "path")).isEqualTo("/catalog");
        assertThat(IntrospectionUtils.getProperty(context, "reloadable")).isEqualTo(true);
        IntrospectionUtils.callMethodN(context, "addParameter",
                new Object[] {"theme", "dark"}, new Class<?>[] {String.class, String.class});
        assertThat(context.findParameter("theme")).isEqualTo("dark");
        SocketProperties properties = new SocketProperties();
        assertThat(IntrospectionUtils.setProperty(properties, "soTimeout", "10000")).isTrue();
        assertThat(properties.getSoTimeout()).isEqualTo(10000);
    }

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
        assertThat(IntrospectionUtils.getProperty(other, "size")).isEqualTo("large");
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

        public String getProperty(String name) {
            return values.get(name);
        }
    }
}
