/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SettableBeanPropertyInnerSetterlessPropertyTest {
    @Test
    void deserializesCollectionThroughGetterWithoutSetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GetterOnlyCollectionBean result = mapper.readValue(
                "{\"values\":[\"alpha\",\"beta\"]}",
                GetterOnlyCollectionBean.class);

        assertThat(result.getValues()).containsExactly("existing", "alpha", "beta");
    }

    public static class GetterOnlyCollectionBean {
        private final List<String> values = new ArrayList<String>(Arrays.asList("existing"));

        public List<String> getValues() {
            return values;
        }
    }
}
