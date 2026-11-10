/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicSerializerFactoryTest {

    @Test
    void serializesAtomicLongAsItsCurrentNumericValue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicLong counter = new AtomicLong(4294967296L);

        assertThat(mapper.writeValueAsString(counter)).isEqualTo("4294967296");

        counter.addAndGet(9L);

        assertThat(mapper.writeValueAsString(counter)).isEqualTo("4294967305");
    }

    @Test
    void serializesAtomicLongValuesInsideContainers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<AtomicLong>> counters = Map.of("counters", List.of(
                new AtomicLong(Long.MIN_VALUE), new AtomicLong(0), new AtomicLong(Long.MAX_VALUE)));

        JsonNode result = mapper.readTree(mapper.writeValueAsString(counters)).get("counters");

        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0).isIntegralNumber()).isTrue();
        assertThat(result.get(0).longValue()).isEqualTo(Long.MIN_VALUE);
        assertThat(result.get(1).longValue()).isZero();
        assertThat(result.get(2).longValue()).isEqualTo(Long.MAX_VALUE);
    }
}
