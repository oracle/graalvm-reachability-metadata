/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.map.UnmodifiableEntrySet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class UnmodifiableEntrySetTest {

    @Test
    public void toArrayWithOversizedTypedArrayReturnsUnmodifiableEntriesInOriginalArray() {
        LinkedHashMap<String, Integer> delegate = new LinkedHashMap<>();
        delegate.put("alpha", 1);
        delegate.put("beta", 2);
        Set<Map.Entry<String, Integer>> entries = unmodifiableEntrySet(delegate);
        Map.Entry<String, Integer>[] target = new Map.Entry[3];

        Map.Entry<String, Integer>[] result = entries.toArray(target);

        assertThat(result).isSameAs(target);
        assertThat(result[0].getKey()).isEqualTo("alpha");
        assertThat(result[0].getValue()).isEqualTo(1);
        assertThat(result[1].getKey()).isEqualTo("beta");
        assertThat(result[1].getValue()).isEqualTo(2);
        assertThat(result[2]).isNull();
        assertThatThrownBy(() -> result[0].setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(delegate).containsEntry("alpha", 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<Map.Entry<String, Integer>> unmodifiableEntrySet(
            Map<String, Integer> delegate) {
        return (Set) UnmodifiableEntrySet.decorate(delegate.entrySet());
    }
}
