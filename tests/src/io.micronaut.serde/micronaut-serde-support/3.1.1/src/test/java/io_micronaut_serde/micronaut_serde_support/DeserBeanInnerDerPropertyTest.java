/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_serde.micronaut_serde_support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.Test;

public class DeserBeanInnerDerPropertyTest {

    @Test
    void mergesIncomingArrayIntoExistingArray() throws Exception {
        ArraySettings settings = new ArraySettings();
        settings.setTags(new String[] {"stable", "native"});

        try (ApplicationContext context = ApplicationContext.run()) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            ArraySettings updated = mapper.updateValue(
                    settings,
                    Argument.of(ArraySettings.class),
                    """
                    {
                      "tags": ["serde", "support"]
                    }
                    """.getBytes(StandardCharsets.UTF_8));

            assertThat(updated).isSameAs(settings);
            assertThat(updated.getTags()).containsExactly("stable", "native", "serde", "support");
        }
    }

    @Serdeable
    public static final class ArraySettings {

        @JsonMerge
        private String[] tags = new String[0];

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }
    }
}
