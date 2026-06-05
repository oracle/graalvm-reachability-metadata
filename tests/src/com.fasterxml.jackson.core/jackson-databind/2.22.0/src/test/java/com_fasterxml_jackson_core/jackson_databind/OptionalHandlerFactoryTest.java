/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.sql.Date;
import java.sql.Time;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionalHandlerFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

    @Test
    void sqlDateSerializerIsDiscoveredByOptionalHandlerFactory() throws JsonProcessingException {
        String json = MAPPER.writeValueAsString(new Date(0));

        assertThat(json).isEqualTo("0");
    }

    @Test
    void sqlTimeSerializerIsDiscoveredByOptionalHandlerFactory() throws JsonProcessingException {
        String json = MAPPER.writeValueAsString(Time.valueOf("00:00:00"));

        assertThat(json).isEqualTo("\"00:00:00\"");
    }

    @Test
    void sqlDateDeserializerIsDiscoveredByOptionalHandlerFactory() throws JsonProcessingException {
        Date value = MAPPER.readValue("0", Date.class);

        assertThat(value).isEqualTo(new Date(0));
    }
}
