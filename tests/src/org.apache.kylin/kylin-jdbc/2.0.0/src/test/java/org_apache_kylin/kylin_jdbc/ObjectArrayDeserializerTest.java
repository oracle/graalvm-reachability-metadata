/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArrayDeserializerTest {
    @Test
    void wrapsASingleJsonValueInATypedArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        String[] values = mapper.readValue("\"only\"", String[].class);

        assertThat(values).containsExactly("only");
        assertThat(values.getClass()).isEqualTo(String[].class);
    }
}
