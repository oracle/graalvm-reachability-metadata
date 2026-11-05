/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.minidev.json.JSONValue;

public class DefaultMapperCollectionTest {
    @Test
    void readsObjectIntoRequestedConcreteMap() throws Exception {
        LinkedHashMap<?, ?> modules = JSONValue.parseWithException(
                """
                {"inventory":"active","billing":"pending"}
                """, LinkedHashMap.class);

        assertThat(modules).isInstanceOf(LinkedHashMap.class);
        assertThat(modules.keySet().toArray()).containsExactly("inventory", "billing");
        assertThat(modules.get("inventory")).isEqualTo("active");
        assertThat(modules.get("billing")).isEqualTo("pending");
    }

    @Test
    void readsArrayIntoRequestedConcreteList() throws Exception {
        LinkedList<?> modules = JSONValue.parseWithException(
                """
                ["inventory","billing",true]
                """, LinkedList.class);

        assertThat(modules).isInstanceOf(LinkedList.class);
        assertThat(modules).hasSize(3);
        assertThat(modules.get(0)).isEqualTo("inventory");
        assertThat(modules.get(1)).isEqualTo("billing");
        assertThat(modules.get(2)).isEqualTo(true);
    }
}
