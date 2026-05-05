/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class CollectionDeserializerTest {
    @Test
    public void deserializesConcreteCollectionWithDefaultConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        NumericValues values = mapper.readValue("[1,2,3]", NumericValues.class);

        assertThat(values).containsExactly(1, 2, 3);
    }

    public static final class NumericValues extends ArrayList<Integer> {
        private static final long serialVersionUID = 1L;
    }
}
