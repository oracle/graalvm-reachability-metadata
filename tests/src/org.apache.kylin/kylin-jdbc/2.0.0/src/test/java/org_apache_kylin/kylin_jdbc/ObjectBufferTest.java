/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectBufferTest {
    @Test
    void completesAMultiChunkTypedArray() throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 40; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(i).append('"');
        }
        json.append(']');

        String[] values = new ObjectMapper().readValue(json.toString(), String[].class);

        assertThat(values).hasSize(40);
        assertThat(values[0]).isEqualTo("0");
        assertThat(values[39]).isEqualTo("39");
    }
}
