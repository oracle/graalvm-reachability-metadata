/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyWriter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        FieldBackedWriterBean bean = new FieldBackedWriterBean();
        String json = JSON_WITH_FIELD_ACCESS.asString(bean);
        Field idField = FieldBackedWriterBean.class.getField("id");
        BeanPropertyWriter writer = new BeanPropertyWriter(0, "id", idField, null);

        assertThat(json).isEqualTo("{\"id\":3}");
        assertThat(writer.getValueFor(bean)).isEqualTo(3);
    }

    @Test
    void serializesGetterBackedProperties() throws Exception {
        GetterBackedWriterBean bean = new GetterBackedWriterBean("Ada");
        String json = JSON_WITH_GETTER_ACCESS.asString(bean);
        Method getName = GetterBackedWriterBean.class.getMethod("getName");
        BeanPropertyWriter writer = new BeanPropertyWriter(0, "name", null, getName);

        assertThat(json).isEqualTo("{\"name\":\"Ada\"}");
        assertThat(bean.getterCalls).isEqualTo(1);
        assertThat(writer.getValueFor(bean)).isEqualTo("Ada");
        assertThat(bean.getterCalls).isEqualTo(2);
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
