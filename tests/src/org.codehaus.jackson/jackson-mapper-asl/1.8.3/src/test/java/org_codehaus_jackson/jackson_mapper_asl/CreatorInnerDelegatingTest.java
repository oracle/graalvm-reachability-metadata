/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class CreatorInnerDelegatingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializesObjectUsingDelegatingConstructor() throws Exception {
        MapConstructorTarget target = MAPPER.readValue("""
                {"name":"constructor","count":3}
                """, MapConstructorTarget.class);

        assertThat(target.name()).isEqualTo("constructor");
        assertThat(target.count()).isEqualTo(3);
    }

    @Test
    public void deserializesObjectUsingDelegatingFactoryMethod() throws Exception {
        MapFactoryTarget target = MAPPER.readValue("""
                {"name":"factory","count":4}
                """, MapFactoryTarget.class);

        assertThat(target.name()).isEqualTo("factory");
        assertThat(target.count()).isEqualTo(4);
        assertThat(target.createdByFactory).isTrue();
    }

    public static final class MapConstructorTarget {
        private final Map<String, Object> values;

        @JsonCreator
        public MapConstructorTarget(Map<String, Object> values) {
            this.values = values;
        }

        public String name() {
            return (String) values.get("name");
        }

        public Integer count() {
            return (Integer) values.get("count");
        }
    }

    public static final class MapFactoryTarget {
        private final Map<String, Object> values;
        final boolean createdByFactory;

        private MapFactoryTarget(Map<String, Object> values, boolean createdByFactory) {
            this.values = values;
            this.createdByFactory = createdByFactory;
        }

        @JsonCreator
        public static MapFactoryTarget from(Map<String, Object> values) {
            return new MapFactoryTarget(values, true);
        }

        public String name() {
            return (String) values.get("name");
        }

        public Integer count() {
            return (Integer) values.get("count");
        }
    }
}
