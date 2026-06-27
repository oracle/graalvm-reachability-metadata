/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonAutoDetect;
import shaded.parquet.com.fasterxml.jackson.annotation.PropertyAccessor;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class SetterlessPropertyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.NONE);

    @Test
    void deserializesIntoCollectionReturnedByGetterWithoutSetter() throws Exception {
        final SetterlessCollectionBean bean = MAPPER.readValue(
                """
                {
                  "ingredients" : [
                    { "name" : "coffee" },
                    { "name" : "milk" }
                  ]
                }
                """,
                SetterlessCollectionBean.class);

        assertThat(bean.getIngredients()).extracting(Ingredient::getName).containsExactly("coffee", "milk");
    }

    public static final class SetterlessCollectionBean {
        private final List<Ingredient> values = new ArrayList<>();

        public List<Ingredient> getIngredients() {
            return values;
        }
    }

    public static final class Ingredient {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
