/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonMerge;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public class MethodPropertyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesJsonObjectFieldsUsingSetterMethods() throws Exception {
        final MethodBackedBean bean = MAPPER.readValue(
                """
                {
                  "name": "coffee"
                }
                """,
                MethodBackedBean.class);

        assertThat(bean.getName()).isEqualTo("coffee");
    }

    @Test
    void deserializesJsonObjectFieldsUsingBuilderMethods() throws Exception {
        final MethodBackedBuiltBean bean = MAPPER.readValue(
                """
                {
                  "name": "espresso"
                }
                """,
                MethodBackedBuiltBean.class);

        assertThat(bean.getName()).isEqualTo("espresso");
    }

    @Test
    void mergesJsonObjectFieldsUsingSetterMethods() throws Exception {
        final MethodBackedMergeBean bean = MAPPER.readValue(
                """
                {
                  "drink": {
                    "name": "latte"
                  }
                }
                """,
                MethodBackedMergeBean.class);

        assertThat(bean.getDrink().getName()).isEqualTo("latte");
    }

    @Test
    void mergesJsonObjectFieldsUsingBuilderMethods() throws Exception {
        final MethodBackedMergeBuiltBean bean = MAPPER.readValue(
                """
                {
                  "drink": {
                    "name": "mocha"
                  }
                }
                """,
                MethodBackedMergeBuiltBean.class);

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

    @JsonDeserialize(builder = MethodBackedBuiltBeanBuilder.class)
    public static final class MethodBackedBuiltBean {
        private final String name;

        private MethodBackedBuiltBean(MethodBackedBuiltBeanBuilder builder) {
            this.name = builder.name;
        }

        public String getName() {
            return name;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class MethodBackedBuiltBeanBuilder {
        private String name;

        public MethodBackedBuiltBeanBuilder name(String name) {
            this.name = name;
            return this;
        }

        public MethodBackedBuiltBean build() {
            return new MethodBackedBuiltBean(this);
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

    @JsonDeserialize(builder = MethodBackedMergeBuiltBeanBuilder.class)
    public static final class MethodBackedMergeBuiltBean {
        private final Drink drink;

        private MethodBackedMergeBuiltBean(MethodBackedMergeBuiltBeanBuilder builder) {
            this.drink = builder.drink;
        }

        public Drink getDrink() {
            return drink;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class MethodBackedMergeBuiltBeanBuilder {
        private Drink drink;

        public Drink getDrink() {
            return drink;
        }

        @JsonMerge
        public MethodBackedMergeBuiltBeanBuilder drink(Drink drink) {
            this.drink = drink;
            return this;
        }

        public MethodBackedMergeBuiltBean build() {
            return new MethodBackedMergeBuiltBean(this);
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
