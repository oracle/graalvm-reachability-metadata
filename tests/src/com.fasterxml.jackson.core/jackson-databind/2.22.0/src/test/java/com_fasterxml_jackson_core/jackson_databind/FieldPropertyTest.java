/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesFieldBackedBeanProperty() throws JsonProcessingException {
        FieldBackedBean bean = MAPPER.readValue("""
                {"name":"coffee"}
                """, FieldBackedBean.class);

        assertThat(bean.name).isEqualTo("coffee");
    }

    @Test
    void deserializesFieldBackedBuilderProperty() throws JsonProcessingException {
        FieldBackedBuiltBean bean = MAPPER.readValue("""
                {"name":"espresso"}
                """, FieldBackedBuiltBean.class);

        assertThat(bean.name).isEqualTo("espresso");
    }

    @Test
    void setsMergedFieldBackedBeanProperty() throws JsonProcessingException {
        FieldBackedMergeBean bean = MAPPER.readValue("""
                {"drink":{"name":"latte"}}
                """, FieldBackedMergeBean.class);

        assertThat(bean.drink.name).isEqualTo("latte");
    }

    @Test
    void setsMergedFieldBackedBuilderPropertyAndReturnsBuilder() throws JsonProcessingException {
        FieldBackedMergeBuiltBean bean = MAPPER.readValue("""
                {"drink":{"name":"mocha"}}
                """, FieldBackedMergeBuiltBean.class);

        assertThat(bean.drink.name).isEqualTo("mocha");
    }

    public static final class FieldBackedBean {
        public String name;
    }

    @JsonDeserialize(builder = FieldBackedBuiltBean.Builder.class)
    public static final class FieldBackedBuiltBean {
        public final String name;

        private FieldBackedBuiltBean(Builder builder) {
            this.name = builder.name;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            public String name;

            public FieldBackedBuiltBean build() {
                return new FieldBackedBuiltBean(this);
            }
        }
    }

    public static final class FieldBackedMergeBean {
        @JsonMerge
        public Drink drink;
    }

    @JsonDeserialize(builder = FieldBackedMergeBuiltBean.Builder.class)
    public static final class FieldBackedMergeBuiltBean {
        public final Drink drink;

        private FieldBackedMergeBuiltBean(Builder builder) {
            this.drink = builder.drink;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            @JsonMerge
            public Drink drink;

            public FieldBackedMergeBuiltBean build() {
                return new FieldBackedMergeBuiltBean(this);
            }
        }
    }

    public static final class Drink {
        public String name;
    }

}
