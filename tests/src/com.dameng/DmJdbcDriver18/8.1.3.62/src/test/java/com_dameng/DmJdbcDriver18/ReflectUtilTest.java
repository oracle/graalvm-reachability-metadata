/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.util.ReflectUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectUtilTest {
    @Test
    void constructsConfiguredTypesWithEachSupportedLoader() {
        String name = ConfigurationBean.class.getName();
        ClassLoader loader = ReflectUtilTest.class.getClassLoader();
        assertEquals("default", ((ConfigurationBean) ReflectUtil.newInstance(name)).value);
        assertEquals("default", ((ConfigurationBean) ReflectUtil.newInstance(name, loader)).value);
        assertEquals("default", ((ConfigurationBean) ReflectUtil.newInstance(name, new ClassLoader[]{loader})).value);
        assertEquals("default", ((ConfigurationBean) ReflectUtil.newInstance(ConfigurationBean.class)).value);

        Class<?>[] types = {String.class};
        Object[] arguments = {"configured"};
        assertEquals("configured", ((ConfigurationBean) ReflectUtil.newInstance(name, types, arguments)).value);
        assertEquals("configured", ((ConfigurationBean) ReflectUtil.newInstance(name, types, arguments, loader)).value);
        assertEquals("configured", ((ConfigurationBean) ReflectUtil.newInstance(ConfigurationBean.class, types, arguments)).value);
    }

    @Test
    void readsAndUpdatesConfiguredFields() {
        ConfigurationBean bean = new ConfigurationBean();
        ReflectUtil.setFieldValue(bean, "value", "updated");
        assertEquals("updated", bean.value);
        assertEquals("updated", ReflectUtil.getFieldValue(bean, "value"));
        assertEquals("value", ReflectUtil.getField(ConfigurationBean.class.getName(), "value").getName());

        String previous = ConfigurationBean.mode;
        try {
            ReflectUtil.setStaticFieldValue(ConfigurationBean.class, "mode", "pooled");
            assertEquals("pooled", ConfigurationBean.mode);
            assertEquals("pooled", ReflectUtil.getStaticFieldValue(ConfigurationBean.class, "mode"));
            assertEquals("pooled", ReflectUtil.getStaticFieldValue(ConfigurationBean.class.getName(), "mode"));
        } finally {
            ConfigurationBean.mode = previous;
        }
    }

    @Test
    void invokesConfiguredOperations() {
        ConfigurationBean bean = new ConfigurationBean("connection");
        assertEquals("connection", ReflectUtil.executeMethod(bean, "getValue"));
        assertEquals("connection!", ReflectUtil.executeMethod(bean, "append", new Class<?>[]{String.class}, new Object[]{"!"}));
        assertEquals("connection", ReflectUtil.executeMethod(ReflectUtil.getMethod(ConfigurationBean.class.getName(), "getValue"), bean));
    }

    public static class ConfigurationBean {
        public static String mode = "direct";
        public String value;

        public ConfigurationBean() {
            this("default");
        }

        public ConfigurationBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public String append(String suffix) {
            return value + suffix;
        }
    }
}
