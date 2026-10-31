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
    void usesGenericPropertyAccessors() {
        BooleanProperties booleanProperties = new BooleanProperties();
        VoidProperties voidProperties = new VoidProperties();
        assertThat(IntrospectionUtils.setProperty(booleanProperties, "color", "blue")).isTrue();
        assertThat(IntrospectionUtils.getProperty(booleanProperties, "color")).isEqualTo("blue");
        assertThat(IntrospectionUtils.setProperty(voidProperties, "color", "green")).isTrue();
        assertThat(IntrospectionUtils.getProperty(voidProperties, "color")).isEqualTo("green");
    }

    public static class BooleanProperties {
        private final Map<String, String> values = new HashMap<>();

        public boolean setProperty(String name, String value) {
            values.put(name, value);
            return true;
        }

        public String getProperty(String name) {
            return values.get(name);
        }
    }

    public static class VoidProperties {
        private final Map<String, String> values = new HashMap<>();

        public void setProperty(String name, String value) {
            values.put(name, value);
        }

        public String getProperty(String name) {
            return values.get(name);
        }
    }
}
