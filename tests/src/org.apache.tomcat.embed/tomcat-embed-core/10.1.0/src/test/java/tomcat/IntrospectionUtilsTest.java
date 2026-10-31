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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntrospectionUtilsTest {
    @Test
    void configuresAndInvokesApplicationBean() throws Exception {
        Properties bean = new Properties();
        assertTrue(IntrospectionUtils.setProperty(bean, "name", "service"));
        assertTrue(IntrospectionUtils.setProperty(bean, "workers", "4"));
        assertTrue(IntrospectionUtils.setProperty(bean, "custom", "value"));
        assertEquals("value", IntrospectionUtils.getProperty(bean, "custom"));
        assertEquals("service:4:orders", IntrospectionUtils.callMethodN(bean, "describe",
                new Object[] {"orders"}, new Class<?>[] {String.class}));
        VoidProperties alternate = new VoidProperties();
        assertTrue(IntrospectionUtils.setProperty(alternate, "setting", "enabled"));
        assertEquals("enabled", alternate.value);
    }

    public static class Properties {
        private String name;
        private int workers;
        private final Map<String, String> values = new HashMap<>();

        public void setName(String name) {
            this.name = name;
        }

        public void setWorkers(int workers) {
            this.workers = workers;
        }

        public boolean setProperty(String key, String value) {
            values.put(key, value);
            return true;
        }

        public String getProperty(String key) {
            return values.get(key);
        }

        public String describe(String suffix) {
            return name + ":" + workers + ":" + suffix;
        }
    }

    public static class VoidProperties {
        private String value;

        public void setProperty(String key, String value) {
            this.value = value;
        }
    }
}
