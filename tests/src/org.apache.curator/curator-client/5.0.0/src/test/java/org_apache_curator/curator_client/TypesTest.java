/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.List;
import java.util.Map;

import org.apache.curator.shaded.com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesTest {
    @Test
    void resolvesGenericAndArrayTypesThroughTypeToken() {
        TypeToken<Map<String, List<Integer>>> token = new TypeToken<>() { };

        assertThat(token.getRawType()).isEqualTo(Map.class);
        assertThat(token.getComponentType()).isNull();
        assertThat(TypeToken.of(String[].class).getComponentType().getRawType()).isEqualTo(String.class);
        assertThat(token.toString()).contains("java.lang.String");
    }
}
