/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BuilderBasedDeserializerTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Product product = mapper.readValue("""
                {\"name\":\"parcel\",\"count\":3}
                """, Product.class);
        assertEquals("parcel", product.name);
        assertEquals(3, product.count);
    }

    @JsonDeserialize(builder = ProductBuilder.class)
    public static class Product {
        public final String name;
        public final int count;

        public Product(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    @JsonPOJOBuilder
    public static class ProductBuilder {
        private String name;
        private int count;

        public ProductBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public ProductBuilder withCount(int count) {
            this.count = count;
            return this;
        }

        public Product build() {
            return new Product(name, count);
        }
    }
}
