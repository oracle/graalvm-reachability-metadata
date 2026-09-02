/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

public class CreatorOptimizerTest {
    @Test
    void deserializesWithAnAnnotatedConstructor() throws Exception {
        ConstructorValue value = BBSerializerModifierTest.MAPPER.readValue(
                """
                { "id": 41, "label": "constructor" }
                """,
                ConstructorValue.class);

        assertThat(value.getId()).isEqualTo(41);
        assertThat(value.getLabel()).isEqualTo("constructor");
    }

    @Test
    void deserializesWithAnAnnotatedStaticFactory() throws Exception {
        FactoryValue value = BBSerializerModifierTest.MAPPER.readValue(
                """
                { "id": 42, "label": "factory" }
                """,
                FactoryValue.class);

        assertThat(value.getId()).isEqualTo(42);
        assertThat(value.getLabel()).isEqualTo("factory");
    }

    public static final class ConstructorValue {
        private final int id;
        private final String label;

        @JsonCreator
        public ConstructorValue(@JsonProperty("id") int id, @JsonProperty("label") String label) {
            this.id = id;
            this.label = label;
        }

        public int getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class FactoryValue {
        private final int id;
        private final String label;

        private FactoryValue(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @JsonCreator
        public static FactoryValue create(@JsonProperty("id") int id, @JsonProperty("label") String label) {
            return new FactoryValue(id, label);
        }

        public int getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }
    }
}
