/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanPropertyWriterDynamicAccessTest {
    private static final JSON JSON_WITH_FIELD_ACCESS = JSON.std
            .with(JSON.Feature.FORCE_REFLECTION_ACCESS)
            .with(JSON.Feature.USE_FIELDS);
    private static final JSON JSON_WITH_GETTER_ACCESS = JSON.std
            .with(JSON.Feature.FORCE_REFLECTION_ACCESS)
            .with(JSON.Feature.WRITE_READONLY_BEAN_PROPERTIES);

    @Test
    void serializesFieldBackedProperties() throws Exception {
        String json = JSON_WITH_FIELD_ACCESS.asString(new FieldBackedWriterBean());

        assertThat(json).isEqualTo("{\"id\":3}");
    }

    @Test
    void serializesGetterBackedProperties() throws Exception {
        GetterBackedWriterBean bean = new GetterBackedWriterBean("Ada");
        String json = JSON_WITH_GETTER_ACCESS.asString(bean);

        assertThat(json).isEqualTo("{\"name\":\"Ada\"}");
        assertThat(bean.getterCalls).isEqualTo(1);
    }

    @Test
    void serializesFieldAndGetterBackedPropertiesInOneWrite() throws Exception {
        GetterBackedWriterBean getterBean = new GetterBackedWriterBean("Ada");

        String json = JSON_WITH_FIELD_ACCESS.asString(List.of(
                new FieldBackedWriterBean(),
                getterBean));

        assertThat(json).isEqualTo("[{\"id\":3},{\"name\":\"Ada\"}]");
        assertThat(getterBean.getterCalls).isEqualTo(1);
    }

    @Test
    void readsFieldBackedValuesThroughBeanPropertyWriter() throws Exception {
        Field field = FieldBackedWriterBean.class.getField("id");
        BeanPropertyWriter writer = new BeanPropertyWriter(0, "id", field, null);

        Object value = writer.getValueFor(new FieldBackedWriterBean());

        assertThat(value).isEqualTo(3);
    }

    @Test
    void invokesGetterBackedValuesThroughBeanPropertyWriter() throws Exception {
        Method getter = GetterBackedWriterBean.class.getMethod("getName");
        BeanPropertyWriter writer = new BeanPropertyWriter(0, "name", null, getter);
        GetterBackedWriterBean bean = new GetterBackedWriterBean("Ada");

        Object value = writer.getValueFor(bean);

        assertThat(value).isEqualTo("Ada");
        assertThat(bean.getterCalls).isEqualTo(1);
    }

    public static final class FieldBackedWriterBean {
        public int id = 3;
    }

    public static final class GetterBackedWriterBean {
        private int getterCalls;
        private final String name;

        public GetterBackedWriterBean(String name) {
            this.name = name;
        }

        public String getName() {
            getterCalls++;
            return name;
        }
    }
}
