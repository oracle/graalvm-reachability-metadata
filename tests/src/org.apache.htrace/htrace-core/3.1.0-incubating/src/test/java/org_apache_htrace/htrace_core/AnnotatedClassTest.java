/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.htrace.fasterxml.jackson.annotation.JsonCreator;
import org.apache.htrace.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.htrace.fasterxml.jackson.annotation.JsonProperty;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotatedClassTest {
    @Test
    void appliesConstructorAndFactoryMixInsDuringDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixInAnnotations(ConstructorBackedBean.class, ConstructorBackedBeanMixin.class);
        mapper.addMixInAnnotations(FactoryBackedBean.class, FactoryBackedBeanMixin.class);

        ConstructorBackedBean constructorBackedBean = mapper.readValue(
                "{\"name\":\"constructed\",\"count\":7}", ConstructorBackedBean.class);
        FactoryBackedBean factoryBackedBean = mapper.readValue(
                "{\"label\":\"created\"}", FactoryBackedBean.class);

        assertThat(constructorBackedBean.name).isEqualTo("constructed");
        assertThat(constructorBackedBean.count).isEqualTo(7);
        assertThat(factoryBackedBean.label).isEqualTo("created");
    }

    @Test
    void appliesFieldAndMethodMixInsDuringRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixInAnnotations(FieldAndMethodBean.class, FieldAndMethodBeanMixin.class);
        mapper.addMixInAnnotations(Object.class, ObjectMethodMixin.class);

        String json = mapper.writeValueAsString(new FieldAndMethodBean("field-text", "method-text"));
        FieldAndMethodBean restored = mapper.readValue(
                "{\"field\":\"restored-field\",\"accessor\":\"restored-method\"}", FieldAndMethodBean.class);

        assertThat(json).contains("\"field\":\"field-text\"");
        assertThat(json).contains("\"accessor\":\"method-text\"");
        assertThat(restored.fieldValue).isEqualTo("restored-field");
        assertThat(restored.getAccessorValue()).isEqualTo("restored-method");
    }

    public static class ConstructorBackedBean {
        private final String name;
        private final int count;

        public ConstructorBackedBean(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    public abstract static class ConstructorBackedBeanMixin {
        @JsonCreator
        public ConstructorBackedBeanMixin(
                @JsonProperty("name") String name,
                @JsonProperty("count") int count) {
        }
    }

    public static class FactoryBackedBean {
        private final String label;

        private FactoryBackedBean(String label) {
            this.label = label;
        }

        public static FactoryBackedBean fromJson(String label) {
            return new FactoryBackedBean(label);
        }
    }

    public abstract static class FactoryBackedBeanMixin {
        @JsonCreator
        public static FactoryBackedBean fromJson(@JsonProperty("label") String label) {
            return null;
        }
    }

    public static class FieldAndMethodBean {
        private String fieldValue;
        private String methodValue;

        public FieldAndMethodBean() {
        }

        public FieldAndMethodBean(String fieldValue, String methodValue) {
            this.fieldValue = fieldValue;
            this.methodValue = methodValue;
        }

        public String getAccessorValue() {
            return methodValue;
        }

        public void setAccessorValue(String methodValue) {
            this.methodValue = methodValue;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public abstract static class FieldAndMethodBeanMixin {
        @JsonProperty("field")
        String fieldValue;

        @JsonProperty("accessor")
        public abstract String getAccessorValue();

        @JsonProperty("accessor")
        public abstract void setAccessorValue(String methodValue);
    }

    public abstract static class ObjectMethodMixin {
        @JsonIgnore
        public String toString() {
            return null;
        }
    }
}
