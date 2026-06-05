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

public class MethodPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesMethodBackedBeanProperty() throws JsonProcessingException {
        MethodBackedBean bean = MAPPER.readValue("""
                {"name":"coffee"}
                """, MethodBackedBean.class);

        assertThat(bean.getName()).isEqualTo("coffee");
    }

    @Test
    void deserializesMethodBackedBuilderPropertyAndReturnsBuilder() throws JsonProcessingException {
        MethodBackedBuiltBean bean = MAPPER.readValue("""
                {"name":"espresso"}
                """, MethodBackedBuiltBean.class);

        assertThat(bean.getName()).isEqualTo("espresso");
    }

    @Test
    void setsMergedMethodBackedBeanProperty() throws JsonProcessingException {
        MethodBackedMergeBean bean = MAPPER.readValue("""
                {"drink":{"name":"latte"}}
                """, MethodBackedMergeBean.class);

        assertThat(bean.getDrink().getName()).isEqualTo("latte");
    }

    @Test
    void setsMergedMethodBackedBuilderPropertyAndReturnsBuilder() throws JsonProcessingException {
        MethodBackedMergeBuiltBean bean = MAPPER.readValue("""
                {"drink":{"name":"mocha"}}
                """, MethodBackedMergeBuiltBean.class);

        assertThat(bean.getDrink().getName()).isEqualTo("mocha");
    }

    public static final class MethodBackedBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonDeserialize(builder = MethodBackedBuiltBean.Builder.class)
    public static final class MethodBackedBuiltBean {
        private final String name;

        private MethodBackedBuiltBean(Builder builder) {
            this.name = builder.name;
        }

        public String getName() {
            return name;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            private String name;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public MethodBackedBuiltBean build() {
                return new MethodBackedBuiltBean(this);
            }
        }
    }

    public static final class MethodBackedMergeBean {
        private Drink drink;

        public Drink getDrink() {
            return drink;
        }

        @JsonMerge
        public void setDrink(Drink drink) {
            this.drink = drink;
        }
    }

    @JsonDeserialize(builder = MethodBackedMergeBuiltBean.Builder.class)
    public static final class MethodBackedMergeBuiltBean {
        private final Drink drink;

        private MethodBackedMergeBuiltBean(Builder builder) {
            this.drink = builder.drink;
        }

        public Drink getDrink() {
            return drink;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            private Drink drink;

            public Drink getDrink() {
                return drink;
            }

            @JsonMerge
            public Builder drink(Drink drink) {
                this.drink = drink;
                return this;
            }

            public MethodBackedMergeBuiltBean build() {
                return new MethodBackedMergeBuiltBean(this);
            }
        }
    }

    public static final class Drink {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
