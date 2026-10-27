/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;

public class ArraysMapperInnerGenericMapperTest {
    @Test
    void parsesJsonArrayIntoObjectArrayType() {
        String[] values = JSONValue.parse("[\"alpha\",\"beta\",\"gamma\"]", String[].class);

        assertThat(values)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma");
    }
}
