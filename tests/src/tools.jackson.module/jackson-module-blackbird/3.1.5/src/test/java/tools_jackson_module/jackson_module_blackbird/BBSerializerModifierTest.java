/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

public class BBSerializerModifierTest {
    @Test
    void serializesMethodBackedPrimitiveStringAndReferenceProperties() throws Exception {
        SerializableBean bean = new SerializableBean(
                17,
                9_000_000_001L,
                true,
                "blackbird",
                new Detail("optimized"));

        JsonNode json = CrossLoaderAccessTest.MAPPER.readTree(
                CrossLoaderAccessTest.MAPPER.writeValueAsString(bean));

        assertThat(json.get("count").asInt()).isEqualTo(17);
        assertThat(json.get("sequence").asLong()).isEqualTo(9_000_000_001L);
        assertThat(json.get("active").asBoolean()).isTrue();
        assertThat(json.get("name").asText()).isEqualTo("blackbird");
        assertThat(json.at("/detail/description").asText()).isEqualTo("optimized");
    }

    public static final class SerializableBean {
        private final int count;
        private final long sequence;
        private final boolean active;
        private final String name;
        private final Detail detail;

        public SerializableBean(int count, long sequence, boolean active, String name, Detail detail) {
            this.count = count;
            this.sequence = sequence;
            this.active = active;
            this.name = name;
            this.detail = detail;
        }

        public int getCount() {
            return count;
        }

        public long getSequence() {
            return sequence;
        }

        public boolean isActive() {
            return active;
        }

        public String getName() {
            return name;
        }

        public Detail getDetail() {
            return detail;
        }
    }

    public static final class Detail {
        private final String description;

        public Detail(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
