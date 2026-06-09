/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonBackReference;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonCreator;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonManagedReference;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public class FieldPropertyTest {
    @Test
    void deserializesJsonObjectFieldsIntoBeanFields() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final FieldBean bean = mapper.readValue(
                """
                {
                  "name": "alpha",
                  "count": 7
                }
                """,
                FieldBean.class);

        assertThat(bean.name).isEqualTo("alpha");
        assertThat(bean.count).isEqualTo(7);
    }

    @Test
    void deserializesJsonObjectFieldsIntoBuilderFields() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final BuiltBean bean = mapper.readValue(
                """
                {
                  "name": "builder-alpha",
                  "count": 11
                }
                """,
                BuiltBean.class);

        assertThat(bean.name).isEqualTo("builder-alpha");
        assertThat(bean.count).isEqualTo(11);
    }

    @Test
    void linksManagedAndBackReferenceFields() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final Parent parent = mapper.readValue(
                """
                {
                  "name": "parent",
                  "child": {
                    "name": "child"
                  }
                }
                """,
                Parent.class);

        assertThat(parent.name).isEqualTo("parent");
        assertThat(parent.child.name).isEqualTo("child");
        assertThat(parent.child.parent).isSameAs(parent);
    }

    public static final class FieldBean {
        public String name;
        public int count;
    }

    @JsonDeserialize(builder = BuiltBeanBuilder.class)
    public static final class BuiltBean {
        public final String name;
        public final int count;

        private BuiltBean(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class BuiltBeanBuilder {
        public String name;
        public int count;

        @JsonCreator
        public BuiltBeanBuilder() {
        }

        public BuiltBean build() {
            return new BuiltBean(name, count);
        }
    }

    public static final class Parent {
        public String name;

        @JsonManagedReference
        public Child child;
    }

    public static final class Child {
        public String name;

        @JsonBackReference
        public Parent parent;
    }
}
