/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.javax_ws_rs_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.ws.rs.core.GenericType;

import org.junit.jupiter.api.Test;

public class GenericTypeTest {
    @Test
    void anonymousSubclassDerivesRawTypeForParameterizedArray() {
        GenericType<List<String>[]> genericType = new GenericType<List<String>[]>() {
        };

        assertThat(genericType.getRawType()).isEqualTo(List[].class);
    }
}
