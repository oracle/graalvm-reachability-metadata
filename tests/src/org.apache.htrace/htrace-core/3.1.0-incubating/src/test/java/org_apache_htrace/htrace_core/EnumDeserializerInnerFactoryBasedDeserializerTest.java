/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonCreator;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumDeserializerInnerFactoryBasedDeserializerTest {
    @Test
    void deserializesEnumWithJsonCreatorFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        TraceKind traceKind = mapper.readValue("\"child-span\"", TraceKind.class);

        assertThat(traceKind).isSameAs(TraceKind.CHILD);
    }

    public enum TraceKind {
        ROOT("root-span"),
        CHILD("child-span");

        private final String jsonName;

        TraceKind(String jsonName) {
            this.jsonName = jsonName;
        }

        @JsonCreator
        public static TraceKind fromJsonName(String jsonName) {
            for (TraceKind traceKind : values()) {
                if (traceKind.jsonName.equals(jsonName)) {
                    return traceKind;
                }
            }
            throw new IllegalArgumentException("Unknown trace kind: " + jsonName);
        }
    }
}
