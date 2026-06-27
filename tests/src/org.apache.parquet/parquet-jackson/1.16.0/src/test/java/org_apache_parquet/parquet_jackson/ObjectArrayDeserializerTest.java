/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.DeserializationFeature;
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectArrayDeserializerTest {
    @Test
    void unwrapsSingleObjectValueIntoTypedObjectArray() throws Exception {
        final ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        final Document[] values = mapper.readValue("{\"name\":\"parquet\"}", Document[].class);

        assertThat(values).extracting(Document::getName).containsExactly("parquet");
    }

    public static class Document {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
