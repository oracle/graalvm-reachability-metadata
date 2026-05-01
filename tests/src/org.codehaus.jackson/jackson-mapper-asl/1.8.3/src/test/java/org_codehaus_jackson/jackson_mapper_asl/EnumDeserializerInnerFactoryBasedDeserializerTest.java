/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumDeserializerInnerFactoryBasedDeserializerTest {
    @Test
    void deserializesEnumWithStringFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        LabeledPriority priority = mapper.readValue("\"urgent\"", LabeledPriority.class);

        assertThat(priority).isSameAs(LabeledPriority.HIGH);
        assertThat(priority.getLabel()).isEqualTo("urgent");
    }

    public enum LabeledPriority {
        LOW("normal"),
        HIGH("urgent");

        private final String label;

        LabeledPriority(String label) {
            this.label = label;
        }

        @JsonCreator
        public static LabeledPriority fromLabel(String label) {
            for (LabeledPriority priority : values()) {
                if (priority.label.equals(label)) {
                    return priority;
                }
            }
            throw new IllegalArgumentException("Unknown priority label: " + label);
        }

        public String getLabel() {
            return label;
        }
    }
}
