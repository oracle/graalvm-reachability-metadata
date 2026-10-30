/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.MappedPropertyDescriptor;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MappedPropertyDescriptorAnonymous1Test {
    @Test
    void discoversAWriteOnlyMappedProperty() throws Exception {
        MappedPropertyDescriptor descriptor = new MappedPropertyDescriptor("option", Settings.class);
        assertEquals(String.class, descriptor.getMappedPropertyType());
        assertNull(descriptor.getMappedReadMethod());
        assertEquals("setOption", descriptor.getMappedWriteMethod().getName());

        Settings settings = new Settings();
        new PropertyUtilsBean().setMappedProperty(settings, "option", "mode", "fast");
        assertEquals("mode=fast", settings.getLastUpdate());
    }

    public static class Settings {
        private String lastUpdate;

        public void setOption(String key, String value) {
            lastUpdate = key + "=" + value;
        }

        public String getLastUpdate() {
            return lastUpdate;
        }
    }
}
