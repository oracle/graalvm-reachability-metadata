/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class CreatorInnerStringBasedTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesStringUsingStringBasedConstructor() throws Exception {
        ConstructorTarget target = MAPPER.readValue("\"constructor-value\"", ConstructorTarget.class);

        assertThat(target.value).isEqualTo("constructor-value");
    }

    @Test
    public void deserializesStringUsingStringBasedFactoryMethod() throws Exception {
        FactoryTarget target = MAPPER.readValue("\"factory-value\"", FactoryTarget.class);

        assertThat(target.value).isEqualTo("factory-value");
        assertThat(target.createdByFactory).isTrue();
    }

    public static final class ConstructorTarget {
        final String value;

        @JsonCreator
        public ConstructorTarget(String value) {
            this.value = value;
        }
    }

    public static final class FactoryTarget {
        final String value;
        final boolean createdByFactory;

        private FactoryTarget(String value, boolean createdByFactory) {
            this.value = value;
            this.createdByFactory = createdByFactory;
        }

        @JsonCreator
        public static FactoryTarget fromString(String value) {
            return new FactoryTarget(value, true);
        }
    }
}
