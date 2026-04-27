/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyOnWriteArrayListInnerCOWSubListTest {
    @Test
    void createsTypedArrayForSubListWhenProvidedArrayIsTooSmall() {
        CopyOnWriteArrayList list = new CopyOnWriteArrayList(new Object[] {"first", "second", "third", "fourth"});
        List subList = list.subList(1, 3);
        String[] tooSmall = new String[] {"sentinel"};

        String[] copied = (String[]) subList.toArray(tooSmall);

        assertThat(copied).isNotSameAs(tooSmall);
        assertThat(copied).containsExactly("second", "third");
    }
}
