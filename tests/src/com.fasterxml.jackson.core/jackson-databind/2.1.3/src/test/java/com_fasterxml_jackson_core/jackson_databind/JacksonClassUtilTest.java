/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.lang.reflect.Constructor;

import com.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonClassUtilTest {

    @Test
    void classUtilFindsClassesConstructorsAndCreatesInstances() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            assertThat(ClassUtil.findClass("java.lang.String")).isEqualTo(String.class);
            Thread.currentThread().setContextClassLoader(null);
            assertThat(ClassUtil.findClass("java.lang.Integer")).isEqualTo(Integer.class);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        Constructor<PrivateConstructorBean> constructor = ClassUtil.findConstructor(PrivateConstructorBean.class, true);
        assertThat(constructor).isNotNull();

        PrivateConstructorBean bean = ClassUtil.createInstance(PrivateConstructorBean.class, true);
        assertThat(bean.value).isEqualTo("created");
    }

    static class PrivateConstructorBean {

        final String value;

        private PrivateConstructorBean() {
            this.value = "created";
        }
    }
}
