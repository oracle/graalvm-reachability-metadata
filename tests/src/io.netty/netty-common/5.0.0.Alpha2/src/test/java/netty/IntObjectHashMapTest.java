/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.collection.IntObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntObjectHashMapTest {
    @Test
    public void createsTypedArrayContainingStoredValues() {
        IntObjectHashMap<String> map = new IntObjectHashMap<>();
        map.put(3, "three");
        map.put(17, "seventeen");

        String[] values = map.values(String.class);

        assertThat(values).containsExactlyInAnyOrder("three", "seventeen");
        assertThat(map.get(3)).isEqualTo("three");
        assertThat(map.get(17)).isEqualTo("seventeen");
    }
}
