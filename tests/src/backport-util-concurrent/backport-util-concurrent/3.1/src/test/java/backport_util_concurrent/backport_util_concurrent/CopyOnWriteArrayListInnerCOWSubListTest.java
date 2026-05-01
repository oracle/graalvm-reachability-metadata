/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyOnWriteArrayListInnerCOWSubListTest {
    @Test
    void toArrayWithUndersizedTypedArrayAllocatesArrayWithRequestedComponentType() {
        CopyOnWriteArrayList list = new CopyOnWriteArrayList(Arrays.asList("before", "alpha", "beta", "after"));
        List subList = list.subList(1, 3);

        String[] values = (String[]) subList.toArray(new String[0]);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly("alpha", "beta");
    }
}
