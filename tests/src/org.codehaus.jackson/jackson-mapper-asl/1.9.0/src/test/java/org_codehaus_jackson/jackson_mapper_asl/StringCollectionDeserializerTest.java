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

public class StringCollectionDeserializerTest {
    @Test
    public void deserializesConcreteStringCollectionWithDefaultConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        StringValues values = mapper.readValue("[\"alpha\",\"beta\",null]", StringValues.class);

        assertThat(values).containsExactly("alpha", "beta", null);
    }

    public static final class StringValues extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
    }
}
