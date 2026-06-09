/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectBufferTest {
    @Test
    void deserializesJsonArrayIntoTypedObjectArray() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final String json = """
                [
                  {"name":"alpha"},
                  {"name":"beta"}
                ]
                """;

        final Record[] records = mapper.readValue(json, Record[].class);

        assertThat(records).extracting(Record::getName).containsExactly("alpha", "beta");
    }

    public static class Record {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
