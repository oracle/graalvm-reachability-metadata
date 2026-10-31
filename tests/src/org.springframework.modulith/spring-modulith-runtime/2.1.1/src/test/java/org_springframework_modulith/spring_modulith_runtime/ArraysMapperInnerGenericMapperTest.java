/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.minidev.json.JSONValue;

public class ArraysMapperInnerGenericMapperTest {
    @Test
    void parsesStringsAndNullIntoTypedArray() throws Exception {
        String[] modules = JSONValue.parseWithException(
                """
                ["inventory",null,"billing"]
                """, String[].class);

        assertThat(modules).containsExactly("inventory", null, "billing");
    }

    @Test
    void preservesComponentTypeForEmptyArray() throws Exception {
        String[] modules = JSONValue.parseWithException("[]", String[].class);

        assertThat(modules).isInstanceOf(String[].class).isEmpty();
    }

    @Test
    void parsesNestedTypedArrays() throws Exception {
        String[][] dependencies = JSONValue.parseWithException(
                """
                [["inventory","catalog"],["billing"]]
                """, String[][].class);

        assertThat(dependencies.length).isEqualTo(2);
        assertThat(dependencies[0]).containsExactly("inventory", "catalog");
        assertThat(dependencies[1]).containsExactly("billing");
    }
}
