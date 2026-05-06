/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class CreatorInnerPropertyBasedTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesObjectUsingPropertyBasedConstructor() throws Exception {
        ConstructorTarget target = MAPPER.readValue("""
                {"name":"constructor","count":3,"enabled":true}
                """, ConstructorTarget.class);

        assertThat(target.name).isEqualTo("constructor");
        assertThat(target.count).isEqualTo(3);
        assertThat(target.enabled).isTrue();
    }

    @Test
    public void deserializesObjectUsingPropertyBasedFactoryMethod() throws Exception {
        FactoryTarget target = MAPPER.readValue("""
                {"name":"factory","count":4,"enabled":false}
                """, FactoryTarget.class);

        assertThat(target.name).isEqualTo("factory");
        assertThat(target.count).isEqualTo(4);
        assertThat(target.enabled).isFalse();
        assertThat(target.createdByFactory).isTrue();
    }

    public static final class ConstructorTarget {
        final String name;
        final int count;
        final boolean enabled;

        @JsonCreator
        public ConstructorTarget(
                @JsonProperty("name") String name,
                @JsonProperty("count") int count,
                @JsonProperty("enabled") boolean enabled) {
            this.name = name;
            this.count = count;
            this.enabled = enabled;
        }
    }

    public static final class FactoryTarget {
        final String name;
        final int count;
        final boolean enabled;
        final boolean createdByFactory;

        private FactoryTarget(String name, int count, boolean enabled, boolean createdByFactory) {
            this.name = name;
            this.count = count;
            this.enabled = enabled;
            this.createdByFactory = createdByFactory;
        }

        @JsonCreator
        public static FactoryTarget fromProperties(
                @JsonProperty("name") String name,
                @JsonProperty("count") int count,
                @JsonProperty("enabled") boolean enabled) {
            return new FactoryTarget(name, count, enabled, true);
        }
    }
}
