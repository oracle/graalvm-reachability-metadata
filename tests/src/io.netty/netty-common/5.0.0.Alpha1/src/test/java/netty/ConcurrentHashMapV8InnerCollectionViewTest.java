/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentHashMapV8InnerCollectionViewTest {
    @Test
    public void typedToArrayAllocatesResultWithRequestedComponentType() {
        ConcurrentHashMapV8<String, Integer> map = new ConcurrentHashMapV8<>();
        map.put("alpha", 1);
        map.put("beta", 2);

        String[] keys = map.keySet().toArray(new String[0]);

        assertThat(keys).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(keys.getClass().getComponentType()).isEqualTo(String.class);
    }
}
