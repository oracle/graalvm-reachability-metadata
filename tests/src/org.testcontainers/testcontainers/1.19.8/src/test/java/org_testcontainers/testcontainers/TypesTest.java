/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.reflect.TypeToken;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesTest {
    @Test
    void resolvesTheRawClassOfAGenericArray() {
        TypeToken<List<String>[]> token = new TypeToken<List<String>[]>() {};

        assertThat(token.getRawType()).isEqualTo(List[].class);
        assertThat(token.getComponentType().getRawType()).isEqualTo(List.class);
    }
}
