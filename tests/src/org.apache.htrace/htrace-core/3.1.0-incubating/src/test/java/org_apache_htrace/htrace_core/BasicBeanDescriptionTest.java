/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonInclude;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicBeanDescriptionTest {
    @Test
    void serializesNonDefaultBeanByComparingWithDefaultInstance() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new TraceSerializationOptions(false, 1, "custom"));

        assertThat(json)
                .contains("\"enabled\":false", "\"label\":\"custom\"")
                .doesNotContain("\"sampleRate\"");
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class TraceSerializationOptions {
        private boolean enabled = true;
        private int sampleRate = 1;
        private String label = "default";

        public TraceSerializationOptions() {
        }

        public TraceSerializationOptions(boolean enabled, int sampleRate, String label) {
            this.enabled = enabled;
            this.sampleRate = sampleRate;
            this.label = label;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public String getLabel() {
            return label;
        }
    }
}
