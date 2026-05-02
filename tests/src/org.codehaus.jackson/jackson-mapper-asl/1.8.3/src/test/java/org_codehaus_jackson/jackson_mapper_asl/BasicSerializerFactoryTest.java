/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicSerializerFactoryTest {
    @Test
    void serializesAtomicScalarsThroughLazySerializerLookup() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(new AtomicBoolean(true))).isEqualTo("true");
        assertThat(mapper.writeValueAsString(new AtomicInteger(37))).isEqualTo("37");
        assertThat(mapper.writeValueAsString(new AtomicLong(9_223_372_036_854_000_000L)))
                .isEqualTo("9223372036854000000");
    }

    @Test
    void serializesReferenceAndClassValuesThroughLazySerializerLookup() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String referenceJson = mapper.writeValueAsString(new AtomicReference<String>("payload"));
        String classJson = mapper.writeValueAsString(String.class);

        assertThat(referenceJson).isEqualTo("\"payload\"");
        assertThat(classJson).isEqualTo("\"java.lang.String\"");
    }
}
