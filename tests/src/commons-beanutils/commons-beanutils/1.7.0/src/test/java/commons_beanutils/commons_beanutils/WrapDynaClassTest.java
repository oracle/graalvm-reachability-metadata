/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.WrapDynaBean;
import org.apache.commons.beanutils.WrapDynaClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class WrapDynaClassTest {
    @Test
    void createsAndUpdatesWrappedJavaBeans() throws Exception {
        WrapDynaClass schema = WrapDynaClass.createDynaClass(SampleBean.class);
        DynaBean first = schema.newInstance();
        DynaBean second = schema.newInstance();
        first.set("name", "Ada");
        first.set("count", 4);
        first.set("tags", 0, "primary");

        SampleBean wrapped = (SampleBean) ((WrapDynaBean) first).getInstance();
        assertEquals("Ada", wrapped.getName());
        assertEquals(4, wrapped.getCount());
        assertEquals("primary", first.get("tags", 0));
        assertEquals("untitled", second.get("name"));
        assertEquals(String.class, schema.getDynaProperty("name").getType());
        assertNotSame(((WrapDynaBean) first).getInstance(), ((WrapDynaBean) second).getInstance());
        assertSame(schema, WrapDynaClass.createDynaClass(SampleBean.class));
    }

    @Test
    void describesAndCreatesBeansWithDiscoveredMappedProperties() throws Exception {
        PreferencesBean bean = new PreferencesBean();
        PropertyUtils.setMappedProperty(bean, "preference", "theme", "dark");

        WrapDynaClass schema = WrapDynaClass.createDynaClass(PreferencesBean.class);
        assertEquals(Map.class, schema.getDynaProperty("preference").getType());
        assertEquals("preference", schema.getDynaProperty("preference").getName());

        DynaBean wrapper = new WrapDynaBean(bean);
        assertSame(schema, wrapper.getDynaClass());
        assertEquals("dark", wrapper.get("preference", "theme"));
        wrapper.set("preference", "theme", "light");
        assertEquals("light", bean.getPreference("theme"));

        DynaBean created = schema.newInstance();
        created.set("preference", "theme", "system");
        assertEquals("system", created.get("preference", "theme"));
        assertEquals("light", bean.getPreference("theme"));
    }

    public static class PreferencesBean {
        private final Map<String, String> preferences = new HashMap<>();

        public String getPreference(String key) {
            return preferences.get(key);
        }

        public void setPreference(String key, String value) {
            preferences.put(key, value);
        }
    }
}
