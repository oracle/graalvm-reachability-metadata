/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotatedClassTest {

    @Test
    void introspectionUsesConstructorsMethodsFieldsAndMixIns() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixInAnnotations(IntrospectedBean.class, IntrospectedBeanMixIn.class);
        mapper.addMixInAnnotations(Object.class, ObjectMixIn.class);

        JavaType beanType = mapper.constructType(IntrospectedBean.class);
        BeanDescription serialization = mapper.getSerializationConfig().introspect(beanType);
        BeanDescription creation = mapper.getDeserializationConfig().introspectForCreation(beanType);

        List<?> properties = serialization.findProperties();
        assertThat(properties).hasSizeGreaterThanOrEqualTo(2);
        assertThat(creation.findDefaultConstructor()).isNotNull();
        assertThat(creation.getConstructors()).hasSize(1);
        assertThat(creation.findFactoryMethod(String.class)).isNotNull();
        assertThat(serialization.findMethod("getName", new Class<?>[0])).isNotNull();
        assertThat(serialization.findMethod("hashCode", new Class<?>[0])).isNotNull();
    }

    static class IntrospectedBean {

        public String fieldValue;
        private String name;

        IntrospectedBean() {
        }

        IntrospectedBean(String name) {
            this.name = name;
        }

        public static IntrospectedBean create(String name) {
            return new IntrospectedBean(name);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    abstract static class IntrospectedBeanMixIn {

        @JsonProperty("fieldValue")
        public String fieldValue;

        @JsonCreator
        IntrospectedBeanMixIn(@JsonProperty("name") String name) {
        }

        @JsonCreator
        public static IntrospectedBean create(@JsonProperty("name") String name) {
            return null;
        }

        @JsonProperty("name")
        abstract String getName();

        @JsonProperty("name")
        abstract void setName(String name);
    }

    abstract static class ObjectMixIn {

        @Override
        public abstract int hashCode();
    }
}
