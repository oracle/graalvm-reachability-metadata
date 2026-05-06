/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import java.util.List;

import javax.ws.rs.core.GenericType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericTypeTest {
    @Test
    public void resolvesGenericArrayRawType() {
        GenericType<List<String>[]> genericType = new GenericType<List<String>[]>() {
        };

        assertThat(genericType.getRawType()).isEqualTo(List[].class);
        assertThat(genericType.getType().getTypeName()).contains("java.util.List<java.lang.String>[]");
    }
}
