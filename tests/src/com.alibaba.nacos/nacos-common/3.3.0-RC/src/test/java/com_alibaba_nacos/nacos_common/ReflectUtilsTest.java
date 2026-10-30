/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectUtilsTest {
    @Test
    void readsBeanFieldsWithAndWithoutDefaultValues() throws Exception {
        Settings settings = new Settings();
        assertEquals("cluster", ReflectUtils.getFieldValue(settings, "mode"));
        assertEquals("cluster", ReflectUtils.getFieldValue(settings, "mode", "standalone"));
        assertEquals("standalone", ReflectUtils.getFieldValue(settings, "absent", "standalone"));
        Field port = Settings.class.getField("port");
        assertEquals(8848, ReflectUtils.getField(port, settings));
    }

    @Test
    void invokesASelectedBeanMethod() throws Exception {
        Method describe = Settings.class.getMethod("describe", String.class);
        assertEquals("node:cluster", ReflectUtils.invokeMethod(describe, new Settings(), "node"));
    }

    public static class Settings {
        private final String mode = "cluster";
        public int port = 8848;

        public String describe(String prefix) {
            return prefix + ":" + mode;
        }
    }
}
