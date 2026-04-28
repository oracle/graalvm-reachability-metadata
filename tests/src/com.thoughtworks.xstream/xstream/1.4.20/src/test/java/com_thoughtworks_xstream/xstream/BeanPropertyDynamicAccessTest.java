/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.lang.reflect.Method;

import com.thoughtworks.xstream.converters.javabean.BeanProperty;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyDynamicAccessTest {
    @Test
    void invokesConfiguredAccessorMethods() throws NoSuchMethodException, IllegalAccessException {
        ReadableWritableBean bean = new ReadableWritableBean();
        Method getter = ReadableWritableBean.class.getMethod("getName");
        Method setter = ReadableWritableBean.class.getMethod("setName", String.class);
        BeanProperty property = new BeanProperty(ReadableWritableBean.class, "name", String.class);
        property.setGetterMethod(getter);
        property.setSetterMethod(setter);

        Object setterResult = property.set(bean, "xstream");
        Object value = property.get(bean);

        assertThat(setterResult).isNull();
        assertThat(value).isEqualTo("xstream");
        assertThat(bean.getName()).isEqualTo("xstream");
        assertThat(property.getBeanClass()).isEqualTo(ReadableWritableBean.class);
        assertThat(property.getName()).isEqualTo("name");
        assertThat(property.getType()).isEqualTo(String.class);
        assertThat(property.isReadable()).isTrue();
        assertThat(property.isWritable()).isTrue();
    }

    public static final class ReadableWritableBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
