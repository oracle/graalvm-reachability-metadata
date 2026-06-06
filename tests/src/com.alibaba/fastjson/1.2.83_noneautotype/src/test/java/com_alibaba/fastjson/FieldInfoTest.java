/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.JavaBeanInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class FieldInfoTest {
    @Test
    void genericArrayFieldTypeIsResolvedWhenParsingParameterizedBeans() {
        ParserConfig config = noAsmParserConfig();
        Type beanType = new TypeReference<GenericArrayBean<String>>() {
        }.getType();

        GenericArrayBean<String> bean = JSON.parseObject("{\"values\":[\"one\",\"two\"]}", beanType, config);

        assertThat(bean.values).isInstanceOf(String[].class);
        assertThat(Arrays.asList(bean.values)).containsExactly("one", "two");
    }

    @Test
    void serializationReadsGetterAndFieldValuesThroughFieldInfo() {
        SerializeConfig config = noAsmSerializeConfig();

        String getterJson = JSON.toJSONString(new GetterBackedBean("getter"), config);
        String fieldJson = JSON.toJSONString(new FieldBackedBean("field"), config);

        assertThat(getterJson).isEqualTo("{\"name\":\"getter\"}");
        assertThat(fieldJson).isEqualTo("{\"name\":\"field\"}");
    }

    @Test
    void setAssignsValuesThroughBeanInfoSetterFieldInfo() throws InvocationTargetException, IllegalAccessException {
        JavaBeanInfo beanInfo = JavaBeanInfo.build(SetterBackedBean.class, SetterBackedBean.class, null);
        FieldInfo fieldInfo = Arrays.stream(beanInfo.fields)
                .filter(field -> "name".equals(field.name))
                .findFirst()
                .orElseThrow(AssertionError::new);
        SetterBackedBean bean = new SetterBackedBean();

        fieldInfo.set(bean, "setter");

        assertThat(bean.getName()).isEqualTo("setter");
    }

    private static ParserConfig noAsmParserConfig() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(false);
        return config;
    }

    private static SerializeConfig noAsmSerializeConfig() {
        SerializeConfig config = new SerializeConfig();
        config.setAsmEnable(false);
        return config;
    }

    public static class GenericArrayBean<T> {
        public T[] values;
    }

    public static class GetterBackedBean {
        private final String name;

        GetterBackedBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class FieldBackedBean {
        public String name;

        FieldBackedBean(String name) {
            this.name = name;
        }
    }

    public static class SetterBackedBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
